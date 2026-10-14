package com.futo.platformplayer.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VoiceInputGainTest {
    @Test fun quietSpeechIsRaisedButSilenceAndHardwareAgcAreUntouched() {
        val gain = VoiceInputGain(true)
        val speech = ShortArray(1600) { if (it % 2 == 0) 300 else -300 }
        repeat(5) { val frame = speech.copyOf(); gain.process(frame, frame.size) }
        val amplified = speech.copyOf(); gain.process(amplified, amplified.size)
        assertTrue(amplified.maxOf { abs(it.toInt()) } >= 1100)
        val silence = ShortArray(1600); gain.process(silence, silence.size)
        assertTrue(silence.all { it == 0.toShort() })
        val original = speech.copyOf(); VoiceInputGain(false).process(original, original.size)
        assertArrayEquals(speech, original)
    }
    @Test fun aSuddenLoudFrameCannotClipAfterQuietSpeech() {
        val gain = VoiceInputGain(true)
        repeat(5) { gain.process(ShortArray(1600) { 300 }, 1600) }
        val loud = ShortArray(1600) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        gain.process(loud, loud.size)
        assertTrue(loud.all { abs(it.toInt()) <= 28000 })
    }
    @Test fun randomInputStaysBoundedAndTheUnfilledBufferTailIsNotModified() {
        val random = kotlin.random.Random(814)
        val gain = VoiceInputGain(true)
        repeat(500) {
            val count = random.nextInt(1, 1601)
            val frame = ShortArray(1600) { random.nextInt(-32768, 32768).toShort() }
            val original = frame.copyOf()
            gain.process(frame, count)
            assertTrue(frame.take(count).all { abs(it.toInt()) <= 28000 })
            assertArrayEquals(original.copyOfRange(count, 1600), frame.copyOfRange(count, 1600))
        }
    }
}
