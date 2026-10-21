package com.futo.platformplayer.voice

import android.content.Context
import androidx.work.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

internal data class VoiceModel(val id: String, val label: String, val url: String, val hash: String,
    val bytes: Long, val format: String, val required: List<String>, val attribution: String = "", val licenseUrl: String = "")

internal object VoiceModels {
    private const val SPEECH = "https://github.com/k2-fsa/sherpa-onnx/releases/download/"
    val catalog = listOf(
        VoiceModel("wake-it", "Comandi rapidi · Italiano · 48 MB", "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
            "9ec65e75861d1c6c2e457cccd932705340dcdf233f5b239f00733b4de0bf3267", 48_000_000, "zip", listOf("am/final.mdl")),
        VoiceModel("wake-en", "Attivazione “ok Grayjoy” / comandi English · 42 MB", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498", 42_000_000, "zip", listOf("am/final.mdl")),
        VoiceModel("whisper", "Whisper Tiny int8 · multilingue · 116 MB download", SPEECH + "asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            "c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1", 116204861, "tar.bz2",
            listOf("tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt")),
        VoiceModel("whisper-base", "Whisper Base int8 · più accurato · 208 MB download", SPEECH + "asr-models/sherpa-onnx-whisper-base.tar.bz2",
            "911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de", 207557382, "tar.bz2",
            listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt")),
        VoiceModel("whisper-small", "Whisper Small int8 · più accurato, più lento · 639 MB download", SPEECH + "asr-models/sherpa-onnx-whisper-small.tar.bz2",
            "486a46afbb7ba798507190ffe02fea2dd726049af212e774537efac6afb210a6", 639387718, "tar.bz2",
            listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt"),
            "Whisper / OpenAI · MIT · conversione sherpa-onnx", "https://github.com/openai/whisper/blob/main/LICENSE"),
        VoiceModel("qwen", "Qwen 3 · 0,6B · 4 bit · 397 MB", "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/50968a4468ef4233ed78cd7c3de230dd1d61a56b/Qwen3-0.6B-Q4_K_M.gguf",
            "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a", 396705472, "gguf", listOf("model.gguf")),
        VoiceModel("qwen-17", "Qwen 3 · 1,7B · 4 bit · 1,11 GB", "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
            "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897", 1107409472, "gguf", listOf("model.gguf"),
            "Qwen / Alibaba · Apache 2.0 · quantizzazione Unsloth", "https://huggingface.co/Qwen/Qwen3-1.7B/blob/main/LICENSE"),
        VoiceModel("qwen-3", "Qwen 2.5 Instruct · 3B · 4 bit · 2,10 GB", "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/7dabda4d13d513e3e842b20f0d435c732f172cbe/qwen2.5-3b-instruct-q4_k_m.gguf",
            "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d", 2104932768, "gguf", listOf("model.gguf"),
            "Qwen / Alibaba · Qwen Research License", "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct/blob/main/LICENSE"),
        VoiceModel("vad", "Rilevamento del parlato · Silero · 0,7 MB", SPEECH + "asr-models/silero_vad.onnx",
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", 643854, "onnx", listOf("silero_vad.onnx")),
        VoiceModel("tts-it", "Piper Paola · Italiano · 21 MB", SPEECH + "tts-models/vits-piper-it_IT-paola-medium-int8.tar.bz2",
            "2b975ed305391c056944a4dde67ee754dd824099503a860295bb4c1d724662d8", 21143212, "tar.bz2", listOf("it_IT-paola-medium.onnx", "tokens.txt", "espeak-ng-data")),
        VoiceModel("tts-en", "Piper Lessac · English · 21 MB", SPEECH + "tts-models/vits-piper-en_US-lessac-medium-int8.tar.bz2",
            "f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9", 20969179, "tar.bz2", listOf("en_US-lessac-medium.onnx", "tokens.txt", "espeak-ng-data")),
        VoiceModel("tts-riccardo", "Piper Riccardo · Italiano · 13 MB", SPEECH + "tts-models/vits-piper-it_IT-riccardo-x_low-int8.tar.bz2",
            "afc2ea457fd45420dbdd87db4b3927b4fc97b00511964d474bb43e03092d37c1", 13329285, "tar.bz2",
            listOf("it_IT-riccardo-x_low.onnx", "tokens.txt", "espeak-ng-data"), "Voce Riccardo · dataset M-AILABS. Attribuzione e condizioni nel modello scaricato."),
        VoiceModel("tts-supertonic", "Supertonic 3 int8 · 10 voci multilingue · 129 MB", SPEECH + "tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2",
            "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427", 128774318, "tar.bz2",
            listOf("duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin"),
            "© 2026 Supertone Inc. · modello OpenRAIL-M; codice MIT. Consulta le condizioni prima del download.",
            "https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE"),
        VoiceModel("tts-kokoro", "Kokoro 1.0 int8 · voci italiane e inglesi · 132 MB", SPEECH + "tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2",
            "4c3052abaa60943a341f193888cf6abd68787dae6ab8ae5c925a706caa247e4e", 132303094, "tar.bz2",
            listOf("model.int8.onnx", "voices.bin", "tokens.txt", "espeak-ng-data"), "Kokoro / hexgrad · Apache 2.0 · licenza inclusa nel download.",
            "https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE"),
    )
    fun selectedLlm(prefs: android.content.SharedPreferences): String =
        prefs.getString("llm_model", "qwen").orEmpty().takeIf { it in setOf("qwen", "qwen-17", "qwen-3") } ?: "qwen"
    fun directory(context: Context, id: String) = recoverVoiceModelDirectory(File(context.noBackupFilesDir, "models/$id"))
    fun installed(context: Context, id: String): Boolean {
        val model = catalog.firstOrNull { it.id == id } ?: return false
        val root = directory(context, id)
        return runCatching { File(root, ".complete").readText().trim() == model.hash }.getOrDefault(false) &&
            model.required.all { File(root, it).exists() }
    }
    fun request(context: Context, id: String) {
        require(catalog.any { it.id == id })
        WorkManager.getInstance(context).enqueueUniqueWork("voice-model-$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<VoiceModelWorker>().setInputData(workDataOf("model" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}

class VoiceModelWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = VoiceModels.catalog.firstOrNull { it.id == inputData.getString("model") }
            ?: return@withContext Result.failure()
        if (VoiceModels.installed(applicationContext, model.id)) return@withContext Result.success()
        val parent = VoiceModels.directory(applicationContext, model.id).parentFile!!
        parent.mkdirs()
        val staging = File(parent, "${model.id}.staging")
        val archive = File(parent, "${model.id}.download")
        var call: okhttp3.Call? = null
        try {
            // Disk pressure must not consume the reserve required by playback/download jobs.
            check(parent.usableSpace > model.bytes * (if (model.format in setOf("gguf", "onnx")) 1 else 3) + 128L * 1024 * 1024) { "Spazio insufficiente per il modello" }
            val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.MINUTES).build()
            call = client.newCall(Request.Builder().url(model.url).build())
            val activeCall = call
            withCancellableVoiceDownload(activeCall) { response ->
                    check(response.isSuccessful) { "Download HTTP ${response.code}" }
                    val total = response.body.contentLength().takeIf { it > 0L } ?: model.bytes
                    response.body.byteStream().use { input -> archive.outputStream().use { output ->
                        val bytes = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastProgress = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(bytes)
                            if (count < 0) break
                            copied += count
                            check(copied <= model.bytes + 2 * 1024 * 1024) { "Model exceeds expected size" }
                            check(parent.usableSpace > 128L * 1024 * 1024) { "Spazio insufficiente" }
                            output.write(bytes, 0, count)
                            if (copied - lastProgress > 1024 * 1024) {
                                setProgress(workDataOf("progress" to (copied.toFloat() / total).coerceIn(0f, 1f) * 0.8f,
                                    "stage" to "download"))
                                lastProgress = copied
                            }
                        }
                    } }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            setProgress(workDataOf("progress" to 0.8f, "stage" to "verify"))
            archive.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { coroutineContext.ensureActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == model.hash) { "Model checksum mismatch" }
            staging.deleteRecursively(); staging.mkdirs()
            setProgress(workDataOf("progress" to 0.85f, "stage" to "unpack"))
            if (model.format in setOf("gguf", "onnx")) check(archive.renameTo(File(staging, model.required.single())))
            else extract(model, archive, staging)
            check(model.required.all { File(staging, it).exists() }) { "Model files incomplete" }
            File(staging, ".complete").writeText(model.hash)
            coroutineContext.ensureActive()
            val target = VoiceModels.directory(applicationContext, model.id)
            installVoiceModelDirectory(staging, target)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { call?.cancel(); throw cancelled }
        catch (error: Exception) {
            Result.failure(workDataOf("error" to (error.message?.take(200) ?: "Download failed")))
        } finally { archive.delete(); staging.deleteRecursively() }
    }

    private suspend fun extract(model: VoiceModel, archive: File, staging: File) {
        var expanded = 0L
        var entries = 0
        suspend fun copy(path: String, input: java.io.InputStream, directory: Boolean) {
            currentCoroutineContext().ensureActive()
            check(++entries <= 5000)
            val relative = path.substringAfter('/', "").replace('\\', '/')
            if (relative.isBlank()) return
            val target = File(staging, relative).canonicalFile
            check(target.toPath().startsWith(staging.canonicalFile.toPath())) { "Invalid archive path" }
            if (model.id.startsWith("whisper") && !directory && relative !in model.required) return
            if (directory) { target.mkdirs(); return }
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes)
                    if (count < 0) break
                    expanded += count
                    check(expanded <= model.bytes * 4)
                    check(staging.usableSpace > 128L * 1024 * 1024)
                    output.write(bytes, 0, count)
                }
            }
        }
        if (model.format == "zip") ZipInputStream(archive.inputStream().buffered(64 * 1024)).use { input ->
            while (true) { val entry = input.nextEntry ?: break; copy(entry.name, input, entry.isDirectory) }
        } else TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered(64 * 1024))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                check(!entry.isSymbolicLink && !entry.isLink) { "Archive links not permitted" }
                copy(entry.name, input, entry.isDirectory)
                // The signed archive contains fp32 duplicates and examples after the int8 files.
                // Stop decompressing as soon as the selected runtime is complete.
                if (model.id.startsWith("whisper") && model.required.all { File(staging, it).isFile }) break
            }
        }
    }
}
