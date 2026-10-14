package com.futo.platformplayer.voice

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsEngineIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun prepareEngines() {
        val required = listOf("tts-it", "tts-en", "tts-riccardo", "tts-kokoro", "tts-supertonic")
        required.filterNot { VoiceModels.installed(context, it) }.forEach { VoiceModels.request(context, it) }
        withTimeout(8 * 60_000L) {
            while (required.any { !VoiceModels.installed(context, it) }) {
                delay(1000)
                required.forEach { id ->
                    val infos = androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork("voice-model-$id").get()
                    if (infos.isNotEmpty() && infos.all { it.state == androidx.work.WorkInfo.State.FAILED })
                        error(infos.last().outputData.getString("error").orEmpty())
                }
            }
        }
    }
    @Test fun allThreeEnginesProduceIntelligibleItalianOffline() = runBlocking {
        prepareEngines()
        val ids = listOf("paola", "riccardo", "kokoro-sara", "kokoro-nicola", "supertonic-it-F1", "supertonic-it-M1")
        val dir = VoiceModels.directory(context, "whisper-base")
        val asr = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(encoder = File(dir, "base-encoder.int8.onnx").path,
                decoder = File(dir, "base-decoder.int8.onnx").path, language = "it"),
            tokens = File(dir, "base-tokens.txt").path, numThreads = 2)))
        try { ids.forEach { id ->
            val preset = TtsVoices.all.first { it.id == id }
            val started = SystemClock.elapsedRealtime()
            LocalTts(context, preset).use { tts ->
                val loaded = SystemClock.elapsedRealtime()
                tts.generate("Riproduco.") // warm kernels before measuring steady state
                val warmed = SystemClock.elapsedRealtime()
                val audio = tts.generate("Per favore, abbassa il volume della musica.")
                val generated = SystemClock.elapsedRealtime()
                assertTrue(id, audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() })
                assertTrue(id, audio.samples.size.toDouble() / audio.sampleRate in 1.0..15.0)
                val stream = asr.createStream()
                val heard = try { stream.acceptWaveform(audio.samples, audio.sampleRate); asr.decode(stream); asr.getResult(stream).text }
                    finally { stream.release() }
                Log.i("VoiceTtsBenchmark", "$id load_ms=${loaded - started} generation_ms=${generated - warmed} audio_ms=${audio.samples.size * 1000L / audio.sampleRate} transcript=$heard")
                assertTrue("$id: $heard", heard.lowercase().contains("volume"))
                // Synthetic test phrases only, for an optional listening comparison.
                audio.save(File(context.cacheDir, "sample-$id.wav").path)
            }
        } } finally { asr.release() }
    }
    @Test fun englishVoicesAndEverySupertonicPresetGenerateValidAudio() = runBlocking {
        prepareEngines()
        listOf("lessac", "kokoro-heart", "kokoro-emma", "kokoro-michael", "supertonic-en-M1").forEach { id ->
            LocalTts(context, TtsVoices.all.first { it.id == id }).use { tts ->
                val audio = tts.generate("Hello, I'm ready to listen to your commands.")
                assertTrue(id, audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() })
                assertTrue(id, audio.samples.size.toDouble() / audio.sampleRate in 1.0..15.0)
            }
        }
        TtsVoices.forLanguage("it").filter { it.modelId == "tts-supertonic" }.forEach { preset ->
            LocalTts(context, preset).use { tts ->
                assertTrue(preset.id, tts.generate("Riproduco.").samples.isNotEmpty())
            }
        }
    }
    @Test fun grayjoyActivationIsRecognizedWithItalianPronunciation() {
        val model = org.vosk.Model(VoiceModels.directory(context, "wake-en").path)
        try { LocalTts(context, TtsVoices.resolve("it", "paola")).use { voice ->
            val audio = voice.generate("Ok, Grey Joy.")
            val size = (audio.samples.size * 16000L / audio.sampleRate).toInt()
            val pcm = ShortArray(size + 16000) { index ->
                if (index >= size) 0 else (audio.samples[(index * audio.sampleRate.toLong() / 16000).toInt()] * 32767).toInt().toShort()
            }
            WakeWordListener(model).use { listener ->
                var found = false
                for (offset in pcm.indices step 1600) {
                    val chunk = pcm.copyOfRange(offset, minOf(offset + 1600, pcm.size))
                    found = listener.accept(chunk, chunk.size) || found
                }
                assertTrue("Italian activation not detected: ${listener.diagnostics}", found)
            }
        } } finally { model.close() }
    }
    @Test fun pregeneratedReplySurvivesSessionRestartAndAvoidsColdTtsLatency() = runBlocking {
        prepareEngines()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val first = OfflineVoiceRuntime(context, scope, "") { if (it == "Risposte comuni pronte") ready.set(true) }
        val second = OfflineVoiceRuntime(context, scope, "")
        val sink = object : VoiceCommandClient {
            override suspend fun call(name: String, arguments: org.json.JSONObject) = org.json.JSONObject().put("ok", true)
        }
        try {
            first.warm()
            withTimeout(90_000) { while (!ready.get()) delay(100) }
            first.releaseHeavyModels()
            // This new runtime has no prepared TTS model: only its persisted stock reply is available.
            second.handle(FloatArray(0), sink, "", {}, "riproduci Summer of 69", SystemClock.elapsedRealtime())
            val latency = context.getSharedPreferences("voice", 0).getLong("last_feedback_ms", Long.MAX_VALUE)
            Log.i("VoiceTtsBenchmark", "cached_reply_feedback_ms=$latency")
            assertTrue("Cached reply took $latency ms", latency < 1000)
        } finally { first.releaseHeavyModels(); second.releaseHeavyModels(); scope.cancel() }
    }
    @Test fun voiceVolumeIsExactAndRestoredOnDisconnectedSession() = runBlocking {
        val first = GrayjoyMcpClient(context); val second = GrayjoyMcpClient(context)
        try {
            first.connect(); second.connect()
            val before = first.call("status")
            first.call("voice_volume", org.json.JSONObject().put("percent", 50)); delay(180)
            assertEquals(.5, first.call("status").getDouble("playerVolume"), .001)
            second.call("voice_volume", org.json.JSONObject().put("percent", 70)); first.close(); delay(200)
            assertEquals(.7, second.call("status").getDouble("playerVolume"), .001)
            assertEquals(before.optBoolean("playWhenReady"), second.call("status").optBoolean("playWhenReady"))
            second.call("voice_volume", org.json.JSONObject().put("percent", -1)); delay(180)
            assertEquals(1.0, second.call("status").getDouble("playerVolume"), .001)
        } finally { first.close(); second.close() }
    }
}
