package com.futo.platformplayer.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceExtendedUnderstandingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun languageModelsRouteNewParaphrasesAndConsentWithoutTemplates() {
        val failures = ArrayList<String>()
        val llm = NativeLlm()
        try {
            val selected = InstrumentationRegistry.getArguments().getString("llmModel")
            for (id in listOf("qwen", "qwen-17", "qwen-3").filter { selected == null || it == selected }) {
                llm.load(File(VoiceModels.directory(context, id), "model.gguf").path)
                llm.prime(commandPrefix("", id), 4)
                for ((text, expected) in listOf(
                    "Avvia Nueva" to "play_named",
                    "Metti in play Nueva" to "play_named",
                    "Riproduci la playlist Nueva" to "play_playlist",
                    "Mi spieghi quali sono le tue funzioni?" to "help",
                    "Vorrei sapere i nomi delle mie liste di riproduzione" to "list_playlists",
                    "Scegli tu un brano da ascoltare" to "random_music",
                    "Could you give Alice a phone call?" to "call_contact")) {
                    val raw = VoiceDecision.parse(llm.interpret(commandPrompt(text, "", modelId = id), COMMAND_GRAMMAR, 4))
                    val decision = validateVoiceDecision(raw, text, false)
                    android.util.Log.i("VoiceExtendedTest", "$id expected=$expected raw=${raw.tool} actual=${decision.tool}")
                    if (decision.tool != expected) failures += "$id: $expected -> ${decision.tool}"
                }
                for ((text, expected) in listOf("Mi sembra un'ottima idea" to "reply_yes", "Preferirei di no" to "reply_no")) {
                    val decision = if (negative(text)) VoiceDecision("reply_no") else VoiceDecision.parse(llm.interpret(commandPrompt(text, "",
                        CONFIRMATION_CONTEXT, id), CONFIRMATION_GRAMMAR, 4))
                    if (decision.tool != expected) failures += "$id: consent $expected -> ${decision.tool}"
                }
            }
        } finally { llm.unload() }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
    @Test fun whisperSmallInt8TranscribesItalianOffline() {
        assertTrue(VoiceModels.installed(context, "whisper-small"))
        val dir = VoiceModels.directory(context, "whisper-small")
        val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(encoder = File(dir, "small-encoder.int8.onnx").path,
                decoder = File(dir, "small-decoder.int8.onnx").path, language = "it"),
            tokens = File(dir, "small-tokens.txt").path, numThreads = 2)))
        try {
            LocalTts(context, TtsVoices.resolve("it", "paola")).use { voice ->
                val audio = voice.generate("Vorrei ascoltare una canzone della mia playlist.")
                val stream = recognizer.createStream()
                val start = android.os.SystemClock.elapsedRealtime()
                try {
                    stream.acceptWaveform(audio.samples + FloatArray(audio.sampleRate / 2), audio.sampleRate); recognizer.decode(stream)
                    val text = answerText(recognizer.getResult(stream).text)
                    android.util.Log.i("VoiceExtendedTest", "Synthetic Italian transcription: $text")
                    assertTrue("Expected meaningful Italian transcription: $text", text.contains("canzone") && text.replace(" ", "").contains("playlist"))
                    android.util.Log.i("VoiceExtendedTest", "Whisper Small int8 transcription ms=${android.os.SystemClock.elapsedRealtime() - start}")
                } finally { stream.release() }
            }
        } finally { recognizer.release() }
    }
}
