package com.futo.platformplayer.voice

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer

@RunWith(AndroidJUnit4::class)
class VoiceReplyLatencyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun shortItalianAnswersSurviveQuietAudioAndTheRealEndpoint() {
        val selected = TtsVoices.selected(context.getSharedPreferences("voice", 0))
        Model(VoiceModels.directory(context, "wake-it").path).use { model ->
            LocalTts(context, selected).use { tts ->
                CommandSpeechDetector(context).use { vad ->
                    for (phrase in listOf("Sì.", "Sì, leggilo.")) {
                        val audio = tts.generate(phrase)
                        Log.i("VoiceReplyTest", "model_tail_ms=${(audio.samples.size - voiceReplyHandoffFrame(audio.samples, audio.sampleRate)) * 1000L / audio.sampleRate}")
                        for (gain in listOf(1f, .12f)) {
                            vad.reset(shortReply = true)
                            val samples = (audio.samples.size * 16000L / audio.sampleRate).toInt()
                            val pcm = ShortArray(samples + 16000) {
                                if (it >= samples) 0 else (audio.samples[(it * audio.sampleRate.toLong() / 16000).toInt()] * gain * 32767)
                                    .toInt().coerceIn(-32768, 32767).toShort()
                            }
                            createReplyRecognizer(model, "it")!!.use { recognizer ->
                                val text = StringBuilder()
                                var confidence = 0.0; var words = 0; var silence = 0
                                val endpoint = ReplyEndpoint()
                                fun collect(json: String) {
                                    val parsed = JSONObject(json); text.append(parsed.optString("text")).append(' ')
                                    parsed.optJSONArray("result")?.let { results -> repeat(results.length()) {
                                        confidence += results.getJSONObject(it).optDouble("conf"); words++
                                    } }
                                }
                                for (offset in pcm.indices step 1600) {
                                    val chunk = pcm.copyOfRange(offset, minOf(offset + 1600, pcm.size))
                                    val voiced = vad.accept(FloatArray(chunk.size) { chunk[it] / 32768f })
                                    if (recognizer.acceptWaveForm(chunk, chunk.size)) collect(recognizer.result)
                                    silence = if (voiced) 0 else silence + 1
                                    val partial = JSONObject(recognizer.partialResult).optString("partial")
                                    val stable = endpoint.accept("$text $partial", true)
                                    if ((vad.heardSpeech || stable) && silence >= 3) break
                                }
                                collect(recognizer.finalResult)
                                val transcript = text.toString().trim()
                                val reliable = words > 0 && reliableQuickReply(transcript, confidence / words, true, vad.heardSpeech)
                                Log.i("VoiceReplyTest", "synthetic=$phrase gain=$gain text=$transcript vad=${vad.heardSpeech} confidence=${confidence / maxOf(1, words)}")
                                assertTrue("Short answer must not be discarded", vad.heardSpeech || reliable)
                                assertEquals("yes", quickConfirmationAnswer(transcript))
                                assertTrue("Common reply should avoid another model pass", reliable)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun replyCaptureOpensBeforeTheSilentDrainFinishes() = runBlocking {
        var opened = 0L
        val start = SystemClock.elapsedRealtime()
        val samples = FloatArray(4800) { (.02 * kotlin.math.sin(it * 2 * Math.PI * 440 / 16000)).toFloat() }
        playVoiceAudio(samples, 16000, tailMillis = 500, onSpeechEnd = { opened = SystemClock.elapsedRealtime() })
        val completed = SystemClock.elapsedRealtime()
        Log.i("VoiceReplyTest", "capture_open_ms=${opened - start} audio_complete_ms=${completed - start}")
        assertTrue(opened > start)
        assertTrue("Native capture must overlap the output safety tail", completed - opened >= 150)
    }

    @Test fun announcementAndQuestionHaveNoSynthesisWaitBetweenThem() = runBlocking {
        val selected = TtsVoices.selected(context.getSharedPreferences("voice", 0))
        LocalTts(context, selected).use { tts ->
            var firstEnded = 0L
            var questionStarted = 0L
            playPreparedSpeech(listOf("Nuovo messaggio da un contatto di prova.", "Devo leggerlo?"),
                prepare = { text -> tts.generate(text).let { GeneratedAudio(trimVoicePadding(it.samples, it.sampleRate), it.sampleRate) } },
                play = { audio, last ->
                    playVoiceAudio(audio.samples, audio.sampleRate, tailMillis = if (last) 500 else 40,
                        onSpeechEnd = if (!last) ({ firstEnded = SystemClock.elapsedRealtime() }) else null,
                        onStart = { if (last) questionStarted = SystemClock.elapsedRealtime() })
                })
            val gap = questionStarted - firstEnded
            Log.i("VoiceReplyTest", "announcement_question_gap_ms=$gap")
            assertTrue("There must not be a cold synthesis wait between the two parts: $gap ms", gap in 0..350)
        }
    }

    @Test fun promptEchoAndRefusalAreNotPositiveConsent() {
        val selected = TtsVoices.selected(context.getSharedPreferences("voice", 0))
        Model(VoiceModels.directory(context, "wake-it").path).use { model ->
            LocalTts(context, selected).use { tts ->
                for (phrase in listOf("Devo leggerlo?", "No.", "Sì, non leggerlo.")) {
                    val audio = tts.generate(phrase)
                    val count = (audio.samples.size * 16000L / audio.sampleRate).toInt()
                    val pcm = ShortArray(count + 16000) { index ->
                        if (index >= count) 0 else (audio.samples[(index * audio.sampleRate.toLong() / 16000).toInt()] * 32767)
                            .toInt().coerceIn(-32768, 32767).toShort()
                    }
                    createReplyRecognizer(model, "it")!!.use { recognizer ->
                        recognizer.acceptWaveForm(pcm, pcm.size)
                        val text = JSONObject(recognizer.finalResult).optString("text")
                        Log.i("VoiceReplyTest", "synthetic_negative=$phrase recognized=$text")
                        assertNotEquals("A prompt echo or refusal must never authorize reading", "yes", quickConfirmationAnswer(text))
                    }
                }
                CommandSpeechDetector(context).use { detector ->
                    detector.reset(shortReply = true)
                    val random = kotlin.random.Random(17)
                    repeat(50) { frame ->
                        detector.accept(FloatArray(1600) { index ->
                            (.025 * kotlin.math.sin((frame * 1600 + index) * 2 * Math.PI * 80 / 16000) + random.nextDouble(-.005, .005)).toFloat()
                        })
                    }
                    assertFalse("Short-answer mode must still reject steady engine noise", detector.heardSpeech)
                }
            }
        }
    }
}
