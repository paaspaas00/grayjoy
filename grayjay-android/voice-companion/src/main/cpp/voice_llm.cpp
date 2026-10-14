#include <jni.h>
#include "llama.h"
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

static std::mutex guard;
static llama_model * model = nullptr;
static std::string model_path;
static llama_context * cached_context = nullptr;
static std::string cached_prefix;
static int prefix_count = 0;
static std::atomic<bool> cancelled{false};
static std::atomic<jlong> active_owner{0};
static std::once_flag initialized;
static std::string utf8(JNIEnv * env, jstring input) {
    const char * chars = env->GetStringUTFChars(input, nullptr);
    std::string value(chars); env->ReleaseStringUTFChars(input, chars); return value;
}
static void fail(JNIEnv * env, const char * text) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text); }

extern "C" JNIEXPORT void JNICALL Java_com_futo_platformplayer_voice_NativeLlm_nativeLoad(JNIEnv * env, jobject, jstring path, jlong owner) {
    std::lock_guard<std::mutex> lock(guard);
    const auto requested_path = utf8(env, path);
    if (model && model_path != requested_path) {
        if (cached_context) { llama_free(cached_context); cached_context = nullptr; }
        cached_prefix.clear(); prefix_count = 0;
        llama_model_free(model); model = nullptr; model_path.clear();
    }
    active_owner = owner;
    if (model) return;
    std::call_once(initialized, [] { llama_backend_init(); });
    auto parameters = llama_model_default_params(); parameters.n_gpu_layers = 0;
    model = llama_model_load_from_file(requested_path.c_str(), parameters);
    if (model) model_path = requested_path;
    if (!model) fail(env, "Cannot load command model");
}
extern "C" JNIEXPORT void JNICALL Java_com_futo_platformplayer_voice_NativeLlm_nativeCancel(JNIEnv *, jobject, jlong owner) {
    if (active_owner.load() == owner) cancelled = true;
}
extern "C" JNIEXPORT jlong JNICALL Java_com_futo_platformplayer_voice_NativeLlm_nativeParameterCount(JNIEnv * env, jobject, jlong owner) {
    std::lock_guard<std::mutex> lock(guard);
    if (!model || active_owner.load() != owner) { fail(env, "Voice session changed"); return 0; }
    return static_cast<jlong>(llama_model_n_params(model));
}
extern "C" JNIEXPORT void JNICALL Java_com_futo_platformplayer_voice_NativeLlm_nativeUnload(JNIEnv *, jobject, jlong owner) {
    std::lock_guard<std::mutex> lock(guard);
    if (active_owner.load() != owner) return;
    active_owner = 0;
    if (cached_context) { llama_free(cached_context); cached_context = nullptr; }
    cached_prefix.clear(); prefix_count = 0;
    if (model) { llama_model_free(model); model = nullptr; }
    model_path.clear();
}
struct Deadline { std::chrono::steady_clock::time_point end; };
static Deadline deadline;
static bool abort_eval(void * data) {
    return cancelled.load() || std::chrono::steady_clock::now() > static_cast<Deadline *>(data)->end;
}
static bool prime_locked(JNIEnv * env, const std::string & prefix, int threads) {
    if (!model) { fail(env, "Command model is not loaded"); return false; }
    if (cached_context && cached_prefix == prefix) return true;
    if (cached_context) { llama_free(cached_context); cached_context = nullptr; }
    cached_prefix.clear(); prefix_count = 0;
    auto params = llama_context_default_params(); params.n_ctx = 1024; params.n_batch = 1024;
    params.n_ubatch = 128; params.n_threads = threads; params.n_threads_batch = threads;
    params.abort_callback = abort_eval; params.abort_callback_data = &deadline;
    cached_context = llama_init_from_model(model, params);
    if (!cached_context) { fail(env, "Cannot allocate command context"); return false; }
    const auto * vocab = llama_model_get_vocab(model);
    const int count = -llama_tokenize(vocab, prefix.c_str(), prefix.size(), nullptr, 0, true, true);
    if (count <= 0 || count > 750) { fail(env, "Command prefix too large"); return false; }
    std::vector<llama_token> tokens(count);
    llama_tokenize(vocab, prefix.c_str(), prefix.size(), tokens.data(), count, true, true);
    std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(llama_batch_ext_init(cached_context), llama_batch_ext_free);
    for (int i = 0; i < count; ++i) {
        const auto index = llama_batch_ext_add_token(batch.get(), 0, tokens[i]);
        llama_batch_ext_set_pos(batch.get(), index, &i);
    }
    llama_batch_ext_set_output_logits(batch.get(), count - 1, true);
    if (llama_process(cached_context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
        fail(env, "Command preparation interrupted"); return false;
    }
    cached_prefix = prefix; prefix_count = count; return true;
}
extern "C" JNIEXPORT void JNICALL Java_com_futo_platformplayer_voice_NativeLlm_nativePrime(
    JNIEnv * env, jobject, jstring prefix, jint threads, jlong owner) {
    std::lock_guard<std::mutex> lock(guard);
    if (active_owner.load() != owner) { fail(env, "Voice session changed"); return; }
    // Large optional models prepare once when listening starts, outside the command path.
    cancelled = false; deadline.end = std::chrono::steady_clock::now() + std::chrono::seconds(60);
    prime_locked(env, utf8(env, prefix), threads);
}
extern "C" JNIEXPORT jstring JNICALL Java_com_futo_platformplayer_voice_NativeLlm_nativeInterpret(
    JNIEnv * env, jobject, jstring promptInput, jstring grammarInput, jint threads, jlong owner) {
    std::lock_guard<std::mutex> lock(guard);
    if (active_owner.load() != owner) { fail(env, "Voice session changed"); return nullptr; }
    if (!model) { fail(env, "Command model is not loaded"); return nullptr; }
    cancelled = false;
    deadline.end = std::chrono::steady_clock::now() + std::chrono::seconds(15);
    const auto * vocab = llama_model_get_vocab(model);
    const auto prompt = utf8(env, promptInput);
    const auto user_start = prompt.rfind("<|im_start|>user\n");
    if (user_start == std::string::npos) { fail(env, "Invalid command template"); return nullptr; }
    const auto prefix = prompt.substr(0, user_start + std::string("<|im_start|>user\n").size());
    if (!prime_locked(env, prefix, threads)) return nullptr;
    if (!llama_memory_seq_rm(llama_get_memory(cached_context), 0, prefix_count, -1)) {
        fail(env, "Cannot reset command context"); return nullptr;
    }
    llama_set_n_threads(cached_context, threads, threads);
    const auto suffix = prompt.substr(prefix.size());
    const int count = -llama_tokenize(vocab, suffix.c_str(), suffix.size(), nullptr, 0, false, true);
    if (count <= 0 || count + prefix_count > 900) { fail(env, "Command context too large"); return nullptr; }
    std::vector<llama_token> tokens(count);
    llama_tokenize(vocab, suffix.c_str(), suffix.size(), tokens.data(), count, false, true);
    std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(llama_batch_ext_init(cached_context), llama_batch_ext_free);
    for (int i = 0; i < count; ++i) {
        const auto index = llama_batch_ext_add_token(batch.get(), 0, tokens[i]);
        const int position = i + prefix_count;
        llama_batch_ext_set_pos(batch.get(), index, &position);
    }
    llama_batch_ext_set_output_logits(batch.get(), count - 1, true);
    auto chainParams = llama_sampler_chain_default_params();
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(chainParams), llama_sampler_free);
    const auto grammar = utf8(env, grammarInput);
    auto * constrained = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
    if (!constrained) { fail(env, "Invalid command grammar"); return nullptr; }
    llama_sampler_chain_add(sampler.get(), constrained);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    std::string output;
    int position = count + prefix_count;
    for (int generated = 0; generated < 96; ++generated) {
        if (abort_eval(&deadline) || llama_process(cached_context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            fail(env, "Command inference interrupted"); return nullptr;
        }
        const auto token = llama_sampler_sample(sampler.get(), cached_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char buffer[512];
        const auto size = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, false);
        if (size < 0) { fail(env, "Invalid output token"); return nullptr; }
        output.append(buffer, size);
        llama_batch_ext_clear(batch.get());
        const auto index = llama_batch_ext_add_token(batch.get(), 0, token);
        llama_batch_ext_set_pos(batch.get(), index, &position);
        llama_batch_ext_set_output_logits(batch.get(), index, true);
        ++position;
    }
    // JNI NewStringUTF uses modified UTF-8; pass standard UTF-8 bytes through String's decoder.
    const auto array = env->NewByteArray(output.size());
    env->SetByteArrayRegion(array, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
    const auto stringClass = env->FindClass("java/lang/String");
    const auto constructor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    const auto encoding = env->NewStringUTF("UTF-8");
    return static_cast<jstring>(env->NewObject(stringClass, constructor, array, encoding));
}
