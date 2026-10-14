package com.futo.platformplayer.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceUnderstandingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun selectableModelsUnderstandParaphrasesAndIgnoreAccidentalSpeech() {
        val llm = NativeLlm()
        try {
            for (id in listOf("qwen", "qwen-17", "qwen-3")) {
                assertTrue("Install $id before the benchmark", VoiceModels.installed(context, id))
                llm.load(File(VoiceModels.directory(context, id), "model.gguf").path)
                val parameters = llm.parameterCount()
                assertTrue("$id actually loaded $parameters parameters", when (id) {
                    "qwen" -> parameters in 400_000_000L..900_000_000L
                    "qwen-17" -> parameters in 1_500_000_000L..1_900_000_000L
                    else -> parameters in 2_500_000_000L..3_500_000_000L
                })
                llm.prime(commandPrefix("", id), 4)
                for ((text, expected) in listOf(
                    "Avvia Nueva" to "play_named", "Metti in play Nueva" to "play_named",
                    "Stoppa" to "pause", "Potresti fermarla un attimo?" to "pause",
                    "Would you mind putting on Bohemian Rhapsody?" to "play_named",
                    "Mi andrebbe di ascoltare Bohemian Rhapsody" to "play_named",
                    "oggi è proprio una bella giornata" to "ignore", "boh non so cosa dire" to "ignore",
                    "Metallica" to "ignore")) {
                    val started = android.os.SystemClock.elapsedRealtime()
                    val direct = fastVoiceCommand(text)
                    if (text.startsWith("Would you") || text.startsWith("Mi andrebbe") || text.startsWith("Potresti"))
                        assertNull("Paraphrases must exercise the model", direct)
                    val decision = direct ?: validateVoiceDecision(VoiceDecision.parse(llm.interpret(
                        commandPrompt(text, "", modelId = id), COMMAND_GRAMMAR, 4)), text, false)
                    Log.i("VoiceUnderstandingTest", "$id expected=$expected actual=${decision.tool} ms=${android.os.SystemClock.elapsedRealtime() - started}")
                    assertEquals("$id: $text", expected, decision.tool)
                }
            }
        } finally { llm.unload() }
    }

    @Test fun silenceAndEngineNoiseAreNotSpeechButAShortCommandIs() = runBlocking {
        if (!VoiceModels.installed(context, "vad")) VoiceModels.request(context, "vad")
        withTimeout(60_000) { while (!VoiceModels.installed(context, "vad")) delay(100) }
        CommandSpeechDetector(context).use { detector ->
            val random = kotlin.random.Random(13)
            repeat(50) { frame ->
                detector.accept(FloatArray(1600) { sample ->
                    (.025 * kotlin.math.sin((frame * 1600 + sample) * 2 * Math.PI * 80 / 16000) + random.nextDouble(-.005, .005)).toFloat()
                })
            }
            assertFalse(detector.heardSpeech)
            detector.reset()
            LocalTts(context, TtsVoices.resolve("it", "paola")).use { voice ->
                val audio = voice.generate("Metti in pausa.")
                val count = (audio.samples.size * 16000L / audio.sampleRate).toInt()
                val samples = FloatArray(count + 16000) { index ->
                    if (index >= count) 0f else audio.samples[(index * audio.sampleRate.toLong() / 16000).toInt()]
                }
                for (offset in samples.indices step 1600) detector.accept(samples.copyOfRange(offset, minOf(offset + 1600, samples.size)))
                assertTrue(detector.heardSpeech)
                detector.reset()
                val quiet = samples.map { it * .025f }.toFloatArray()
                for (offset in quiet.indices step 1600) detector.accept(quiet.copyOfRange(offset, minOf(offset + 1600, quiet.size)))
                assertTrue("Quiet real speech must still be accepted", detector.heardSpeech)
            }
            detector.reset()
            repeat(50) { detector.accept(FloatArray(1600)) }
            assertFalse(detector.heardSpeech)
        }
    }
}
