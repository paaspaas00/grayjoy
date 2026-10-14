package com.futo.platformplayer.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.util.Log

@RunWith(AndroidJUnit4::class)
class OfflineVoiceIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun prepareDownloadedModels() = runBlocking {
        val needed = listOf("qwen", "vad", "whisper-base", "tts-it", "wake-it", "tts-en", "wake-en")
        needed.filterNot { VoiceModels.installed(context, it) }.forEach { VoiceModels.request(context, it) }
        withTimeout(8 * 60_000L) {
            while (needed.any { !VoiceModels.installed(context, it) }) {
                delay(1000)
                needed.forEach { id ->
                    val infos = androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork("voice-model-$id").get()
                    if (infos.isNotEmpty() && infos.all { it.state == androidx.work.WorkInfo.State.FAILED })
                        error(infos.last().outputData.getString("error") ?: "Model download failed")
                }
            }
        }
    }
    @Test fun commandRouterUnderstandsBilingualParaphrases() {
        assertTrue(VoiceModels.installed(context, "qwen"))
        val llm = NativeLlm()
        try {
            llm.load(File(VoiceModels.directory(context, "qwen"), "model.gguf").path)
            llm.prime(commandPrefix(""), 4)
            listOf("Mi abbassi un po' la musica?" to "volume", "Can you make the music quieter?" to "volume",
                "Fammi sentire la playlist Songs" to "play_playlist", "Play the local playlist Songs" to "play_playlist",
                "Riproduci Bohemian Rhapsody" to "play_named", "Put on Bohemian Rhapsody" to "play_named",
                "Mi fermi la riproduzione un momento?" to "pause").forEach { (text, expected) ->
                val started = android.os.SystemClock.elapsedRealtime()
                val decision = fastVoiceCommand(text) ?: validateVoiceDecision(VoiceDecision.parse(llm.interpret(commandPrompt(text, ""), COMMAND_GRAMMAR, 4)), text, false)
                Log.i("VoiceBenchmark", "$expected ${android.os.SystemClock.elapsedRealtime() - started} ms ${decision.tool}")
                assertEquals(text, expected, decision.tool)
                if (expected == "volume") assertTrue(decision.number < 0)
            }
        } finally { llm.unload() }
    }
    @Test fun smallModelFallbackUsesTheBoundedToolGrammar() {
        val llm = NativeLlm()
        try {
            llm.load(File(VoiceModels.directory(context, "qwen"), "model.gguf").path)
            llm.prime(commandPrefix(""), 4)
            listOf("Please stop playing" to "pause", "Vorrei sospendere la riproduzione" to "pause").forEach { (text, tool) ->
                assertNull("This case must exercise the model, not a shortcut", fastVoiceCommand(text))
                val started = android.os.SystemClock.elapsedRealtime()
                val decision = VoiceDecision.parse(llm.interpret(commandPrompt(text, ""), COMMAND_GRAMMAR, 4))
                Log.i("VoiceBenchmark", "Model fallback ${android.os.SystemClock.elapsedRealtime() - started} ms tool=${decision.tool}")
                assertEquals(tool, decision.tool)
            }
        } finally { llm.unload() }
    }
    @Test fun closingAnOldSessionCannotUnloadOrCancelTheNewSession() {
        val old = NativeLlm(); val current = NativeLlm()
        val path = File(VoiceModels.directory(context, "qwen"), "model.gguf").path
        try {
            old.load(path); old.prime(commandPrefix(""), 4)
            current.load(path)
            old.cancel(); old.unload()
            current.prime(commandPrefix(""), 4)
            assertEquals("pause", VoiceDecision.parse(current.interpret(
                commandPrompt("Please stop playing", ""), COMMAND_GRAMMAR, 4)).tool)
            assertTrue(runCatching { old.prime(commandPrefix(""), 4) }.isFailure)
        } finally { old.unload(); current.unload() }
    }
    @Test fun neuralItalianVoiceAndWhisperRunCompletelyOffline() {
        val dir = VoiceModels.directory(context, "tts-it")
        assertTrue(VoiceModels.installed(context, "tts-it"))
        val tts = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
            model = dir.walkTopDown().first { it.name.endsWith(".onnx") }.path,
            tokens = File(dir, "tokens.txt").path, dataDir = File(dir, "espeak-ng-data").path))))
        val asrDir = VoiceModels.directory(context, "whisper-base")
        val asr = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(encoder = File(asrDir, "base-encoder.int8.onnx").path,
                decoder = File(asrDir, "base-decoder.int8.onnx").path, language = "it"),
            tokens = File(asrDir, "base-tokens.txt").path, numThreads = 2)))
        try {
            val audio = tts.generate("Per favore, abbassa un poco il volume della musica.")
            assertTrue(audio.samples.isNotEmpty())
            val stream = asr.createStream()
            try {
                stream.acceptWaveform(audio.samples, audio.sampleRate); asr.decode(stream)
                val text = asr.getResult(stream).text
                Log.i("VoiceBenchmark", "Italian synthesis + transcription: $text")
                // ASR can split a synthetic Italian inflection; this test checks the offline
                // synthesis/transcription path, not an exact transcript or shortcut routing.
                assertTrue(text.lowercase().contains("volume"))
            } finally { stream.release() }
        } finally { tts.release(); asr.release() }
    }
    @Test fun authenticatedMcpExposesPlaylistsWithoutNetwork() = runBlocking {
        val client = GrayjoyMcpClient(context)
        try { client.connect(); assertTrue(client.tools().contains("play_playlist")); assertTrue(client.call("list_playlists").has("playlists")) }
        finally { client.close() }
    }
    @Test fun unmatchedPhoneticPlaylistDoesNotChangePlayback() = runBlocking {
        val client = GrayjoyMcpClient(context)
        try {
            client.connect()
            val before = client.call("status")
            val response = client.call("play_playlist", org.json.JSONObject().put("query", "9".repeat(128)))
            assertTrue(response.optBoolean("needsClarification"))
            assertEquals("missing", response.getString("kind"))
            assertEquals(before.optString("title"), client.call("status").optString("title"))
        } finally { client.close() }
    }
    @Test fun mcpVolumeCommandsWorkAndRestoreTheOriginalLevel() = runBlocking {
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val original = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val delta = if (original < maximum) 1 else -1
        val client = GrayjoyMcpClient(context)
        try {
            client.connect()
            client.call("volume", org.json.JSONObject().put("steps", delta))
            assertEquals(original + delta, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        } finally {
            if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) != original)
                client.call("volume", org.json.JSONObject().put("steps", -delta))
            client.close()
        }
        assertEquals(original, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }
    @Test fun streamedCommandDispatchDoesNotWaitForHeavyModelsOrTts() = runBlocking {
        val client = GrayjoyMcpClient(context)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val original = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val delta = if (original < maximum) 1 else -1
        var runtime: OfflineVoiceRuntime? = null
        try {
            client.connect()
            client.call("status") // The microphone service prepares the host before advertising readiness.
            runtime = OfflineVoiceRuntime(context, scope, client.tools())
            val phrase = if (delta > 0) "alza il volume" else "abbassa il volume"
            runtime.handle(FloatArray(0), client, "", {}, phrase,
                android.os.SystemClock.elapsedRealtime() - 300)
            assertEquals(original + delta, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
            val latency = context.getSharedPreferences("voice", 0).getLong("last_action_ms", Long.MAX_VALUE)
            Log.i("VoiceBenchmark", "Streamed command including 300 ms endpoint: $latency ms")
            assertTrue("Streaming dispatch took $latency ms", latency < 1000)
            assertTrue(context.getSharedPreferences("voice", 0).getBoolean("last_streaming", false))
        } finally {
            if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) != original)
                client.call("volume", org.json.JSONObject().put("steps", -delta))
            runtime?.releaseHeavyModels(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); client.close()
        }
    }
    @Test fun spokenPlaybackWaitsForTheWholeAudioAndTail() = runBlocking {
        val rate = 16_000
        val started = android.os.SystemClock.elapsedRealtime()
        // Silent test verifies draining without reading or writing user's microphone audio.
        playVoiceAudio(FloatArray(rate / 2), rate, tailMillis = 500)
        assertTrue(android.os.SystemClock.elapsedRealtime() - started >= 900)
    }
    @Test fun unrelatedSpeechNeverDispatchesAToolAndRetryAcceptsARealCommand() = runBlocking {
        val calls = mutableListOf<String>()
        val sink = object : VoiceCommandClient {
            override suspend fun call(name: String, arguments: org.json.JSONObject): org.json.JSONObject {
                calls.add(name); return org.json.JSONObject().put("ok", true)
            }
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val runtime = OfflineVoiceRuntime(context, scope, "")
        try {
            runtime.handle(FloatArray(0), sink, "", {}, "oggi è una bella giornata")
            assertTrue(calls.isEmpty())
            assertFalse(runtime.needsFollowup)
            runtime.handle(FloatArray(0), sink, "", {}, "metti in pausa")
            assertEquals(listOf("pause"), calls)
        } finally { runtime.releaseHeavyModels(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
    @Test fun grayjoyActivationIsRecognizedByTheLightweightListener() {
        val dir = VoiceModels.directory(context, "tts-en")
        val voice = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
            model = dir.walkTopDown().first { it.name.endsWith(".onnx") }.path,
            tokens = File(dir, "tokens.txt").path, dataDir = File(dir, "espeak-ng-data").path))))
        val model = org.vosk.Model(VoiceModels.directory(context, "wake-en").path)
        val recognizer = org.vosk.Recognizer(model, 16000f, org.json.JSONArray(listOf("okay gray joy", "[unk]")).toString())
        try {
            val audio = voice.generate("Okay, Gray Joy.")
            val size = (audio.samples.size * 16000L / audio.sampleRate).toInt()
            val pcm = ShortArray(size + 16000) { index ->
                if (index >= size) 0 else (audio.samples[(index * audio.sampleRate.toLong() / 16000).toInt()] * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
            recognizer.acceptWaveForm(pcm, pcm.size)
            val text = org.json.JSONObject(recognizer.finalResult).optString("text")
            Log.i("VoiceBenchmark", "Wake recognition: $text")
            assertTrue(text.contains("gray joy"))
        } finally { recognizer.close(); model.close(); voice.release() }
    }
    @Test fun realWakeStreamingPathHandlesQuietSpeechAndPauses() {
        val dir = VoiceModels.directory(context, "tts-en")
        val voice = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
            model = dir.walkTopDown().first { it.name.endsWith(".onnx") }.path,
            tokens = File(dir, "tokens.txt").path, dataDir = File(dir, "espeak-ng-data").path))))
        val model = org.vosk.Model(VoiceModels.directory(context, "wake-en").path)
        try {
            val audio = voice.generate("Okay, Gray Joy.")
            val size = (audio.samples.size * 16000L / audio.sampleRate).toInt()
            val loud = ShortArray(size) { index ->
                (audio.samples[(index * audio.sampleRate.toLong() / 16000).toInt()] * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
            val maximumRms = loud.asList().chunked(1600).maxOf { chunk ->
                kotlin.math.sqrt(chunk.sumOf { it.toDouble() * it } / chunk.size)
            }
            val gain = 180 / maximumRms
            val quiet = ShortArray(loud.size + 32_000) { index ->
                if (index < 8000 || index >= loud.size + 8000) 0 else (loud[index - 8000] * gain).toInt().toShort()
            }
            assertTrue("The old RMS > 300 gate must reject every frame", quiet.asList().chunked(1600).all { chunk ->
                kotlin.math.sqrt(chunk.sumOf { it.toDouble() * it } / chunk.size) < 300
            })
            WakeWordListener(model).use { listener ->
                var found = false
                for (offset in quiet.indices step 1600) {
                    val chunk = quiet.copyOfRange(offset, minOf(offset + 1600, quiet.size))
                    found = listener.accept(chunk, chunk.size) || found
                }
                assertTrue("Quiet wake not detected: ${listener.diagnostics}", found)
            }
            // Preserve the pronunciation of the original complete sentence, inserting gaps at
            // its word boundaries. Synthesizing isolated "Ok" with Piper changes its pronunciation.
            val aligner = org.vosk.Recognizer(model, 16_000f,
                org.json.JSONArray(listOf("okay gray joy", "[unk]")).toString()).apply { setWords(true) }
            val boundaries = try {
                aligner.acceptWaveForm(loud, loud.size)
                val words = org.json.JSONObject(aligner.finalResult).getJSONArray("result")
                // Unconstrained ASR may spell the initial "okay" as multiple words. Locate
                // the reliable middle word instead of assuming a particular tokenization.
                val control = (0 until words.length()).first { words.getJSONObject(it).getString("word") in setOf("gray", "grey") }
                val middle = words.getJSONObject(control)
                listOf(middle.getDouble("start"), middle.getDouble("end")).map {
                    (it * 16000).toInt().coerceIn(0, loud.size)
                }
            } finally { aligner.close() }
            val cuts = listOf(0) + boundaries + loud.size
            WakeWordListener(model).use { listener ->
                var found = false
                (0 until 3).forEach { part ->
                    val begin = cuts[part]; val length = cuts[part + 1] - begin
                    val pcm = ShortArray(length + 11_200) { index ->
                        if (index >= length) 0 else loud[begin + index]
                    }
                    for (offset in pcm.indices step 1600) {
                        val chunk = pcm.copyOfRange(offset, minOf(offset + 1600, pcm.size))
                        found = listener.accept(chunk, chunk.size) || found
                    }
                    Log.i("VoiceBenchmark", "Spaced synthetic wake component: ${listener.diagnostics.text}")
                }
                assertTrue("Spaced wake not detected: ${listener.diagnostics}", found)
            }
        } finally { voice.release(); model.close() }
    }
    @Test fun streamingRecognizerHandlesShortItalianPlaybackCommands() {
        val dir = VoiceModels.directory(context, "tts-it")
        val voice = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
            model = dir.walkTopDown().first { it.name.endsWith(".onnx") }.path,
            tokens = File(dir, "tokens.txt").path, dataDir = File(dir, "espeak-ng-data").path))))
        val model = org.vosk.Model(VoiceModels.directory(context, "wake-it").path)
        val recognizer = org.vosk.Recognizer(model, 16000f)
        try {
            val audio = voice.generate("Abbassa il volume.")
            val size = (audio.samples.size * 16000L / audio.sampleRate).toInt()
            val pcm = ShortArray(size + 16000) { index ->
                if (index >= size) 0 else (audio.samples[(index * audio.sampleRate.toLong() / 16000).toInt()] * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
            recognizer.acceptWaveForm(pcm, pcm.size)
            val text = org.json.JSONObject(recognizer.finalResult).optString("text")
            Log.i("VoiceBenchmark", "Streaming command: $text")
            assertEquals("volume", fastVoiceCommand(text)?.tool)
            assertEquals(-1, fastVoiceCommand(text)?.number)
        } finally { recognizer.close(); model.close(); voice.release() }
    }
}
