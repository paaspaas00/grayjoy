package com.futo.platformplayer.compose.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    @Test
    fun decodedAudioIsOnlyAnalyzedWhileVisualizationIsEnabled() {
        val emitted = mutableListOf<List<Float>>()
        val analyzer = AudioSpectrumAnalyzer(emitted::add)
        val pcm = ByteBuffer.allocate(2_048 * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { repeat(2_048) { putShort(1_000.toShort()) }; flip() }

        analyzer.flush(48_000, 1, C.ENCODING_PCM_16BIT)
        analyzer.handleBuffer(pcm)
        assertTrue(emitted.isEmpty())

        analyzer.setEnabled(true)
        analyzer.handleBuffer(pcm)
        assertEquals(2, emitted.size)

        analyzer.setEnabled(false)
        analyzer.handleBuffer(pcm)
        assertEquals(3, emitted.size)
        assertTrue(emitted.last().all { it == 0f })
    }

    private fun spectrumForTone(frequencyHz: Float, bandCount: Int = 56): FloatArray {
        val sampleRateHz = 48_000
        val samples = FloatArray(2_048) { index ->
            (sin(2.0 * PI * frequencyHz * index / sampleRateHz) * 0.75).toFloat()
        }
        return AudioSpectrumMath.calculate(samples, sampleRateHz, bandCount)
    }
}
