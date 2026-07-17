package com.futo.platformplayer.compose.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioSpectrumAnalyzerTest {
    @Test
    fun silenceProducesNoFrequencyEnergy() {
        val spectrum = AudioSpectrumMath.calculate(
            samples = FloatArray(2_048),
            sampleRateHz = 48_000,
            bandCount = 56,
        )

        assertTrue(spectrum.all { it == 0f })
    }

    @Test
    fun dominantBandTracksTheDecodedToneFrequency() {
        val lowTone = spectrumForTone(440f)
        val highTone = spectrumForTone(4_000f)

        val lowPeak = lowTone.indices.maxByOrNull(lowTone::get)!!
        val highPeak = highTone.indices.maxByOrNull(highTone::get)!!
        assertTrue(lowTone[lowPeak] > 0.25f)
        assertTrue(highTone[highPeak] > 0.25f)
        assertTrue(highPeak > lowPeak + 12)
    }

    @Test
    fun outputAlwaysMatchesRequestedBandCount() {
        assertEquals(32, spectrumForTone(1_000f, bandCount = 32).size)
    }

    private fun spectrumForTone(frequencyHz: Float, bandCount: Int = 56): FloatArray {
        val sampleRateHz = 48_000
        val samples = FloatArray(2_048) { index ->
            (sin(2.0 * PI * frequencyHz * index / sampleRateHz) * 0.75).toFloat()
        }
        return AudioSpectrumMath.calculate(samples, sampleRateHz, bandCount)
    }
}
