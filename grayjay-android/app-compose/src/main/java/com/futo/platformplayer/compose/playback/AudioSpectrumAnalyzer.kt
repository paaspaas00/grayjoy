package com.futo.platformplayer.compose.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Observes the decoded PCM sent to ExoPlayer's audio sink. The input buffer is read through a
 * duplicate, so visualization never consumes or changes the samples used for playback.
 */
@UnstableApi
internal class AudioSpectrumAnalyzer(
    private val onSpectrum: (List<Float>) -> Unit,
) : TeeAudioProcessor.AudioBufferSink {
    @Volatile
    private var enabled = false
    private var sampleRateHz = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private val sampleWindow = FloatArray(WINDOW_SIZE)
    private var sampleCount = 0
    private var smoothedSpectrum = FloatArray(BAND_COUNT)

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        sampleCount = 0
        smoothedSpectrum.fill(0f)
        onSpectrum(EMPTY_SPECTRUM)
    }

    @Synchronized
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        sampleCount = 0
        smoothedSpectrum.fill(0f)
        if (enabled) onSpectrum(EMPTY_SPECTRUM)
    }

    @Synchronized
    override fun handleBuffer(buffer: ByteBuffer) {
        if (!enabled) return
        val bytesPerSample = bytesPerSample(encoding)
        if (sampleRateHz <= 0 || channelCount <= 0 || bytesPerSample == 0) return

        val input = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameSize = bytesPerSample * channelCount
        while (input.remaining() >= frameSize) {
            var monoSample = 0f
            repeat(channelCount) {
                monoSample += readSample(input, encoding)
            }
            sampleWindow[sampleCount++] = monoSample / channelCount
            if (sampleCount == WINDOW_SIZE) {
                val measured = AudioSpectrumMath.calculate(
                    samples = sampleWindow,
                    sampleRateHz = sampleRateHz,
                    bandCount = BAND_COUNT,
                )
                for (index in measured.indices) {
                    val release = smoothedSpectrum[index] * 0.76f
                    smoothedSpectrum[index] = max(measured[index], release).coerceIn(0f, 1f)
                }
                onSpectrum(smoothedSpectrum.toList())
                sampleCount = 0
            }
        }
    }

    private fun readSample(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
        C.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xFF) - 128) / 128f
        C.ENCODING_PCM_16BIT -> buffer.short / 32768f
        C.ENCODING_PCM_24BIT -> {
            val b0 = buffer.get().toInt() and 0xFF
            val b1 = buffer.get().toInt() and 0xFF
            val b2 = buffer.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608f
        }
        C.ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648f
        C.ENCODING_PCM_FLOAT -> buffer.float.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        else -> 0f
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private companion object {
        const val WINDOW_SIZE = 2_048
        const val BAND_COUNT = 56
        val EMPTY_SPECTRUM = List(BAND_COUNT) { 0f }
    }
}

internal object AudioSpectrumMath {
    fun calculate(
        samples: FloatArray,
        sampleRateHz: Int,
        bandCount: Int,
    ): FloatArray {
        require(samples.isNotEmpty() && samples.size.countOneBits() == 1)
        if (sampleRateHz <= 0 || bandCount <= 0) return FloatArray(max(0, bandCount))

        val size = samples.size
        val real = FloatArray(size)
        val imaginary = FloatArray(size)
        for (index in samples.indices) {
            val hann = 0.5f - 0.5f * cos((2.0 * PI * index / (size - 1)).toFloat())
            real[index] = samples[index] * hann
        }
        fft(real, imaginary)

        val nyquist = sampleRateHz / 2f
        val minimumHz = min(35f, nyquist)
        val maximumHz = min(18_000f, nyquist)
        if (maximumHz <= minimumHz) return FloatArray(bandCount)
        val output = FloatArray(bandCount)
        for (band in 0 until bandCount) {
            val lowFraction = band.toFloat() / bandCount
            val highFraction = (band + 1).toFloat() / bandCount
            val lowHz = minimumHz * (maximumHz / minimumHz).toDouble().pow(lowFraction.toDouble()).toFloat()
            val highHz = minimumHz * (maximumHz / minimumHz).toDouble().pow(highFraction.toDouble()).toFloat()
            val lowBin = max(1, (lowHz * size / sampleRateHz).roundToInt())
            val highBin = max(lowBin, (highHz * size / sampleRateHz).roundToInt())
                .coerceAtMost(size / 2 - 1)
            var squaredMagnitude = 0f
            var binCount = 0
            for (bin in lowBin..highBin) {
                squaredMagnitude += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                binCount++
            }
            val rmsMagnitude = sqrt(squaredMagnitude / max(1, binCount)) / (size * 0.5f)
            output[band] = (ln1p(rmsMagnitude * 90f) / ln1p(90f)).coerceIn(0f, 1f)
        }
        return output
    }

    private fun fft(real: FloatArray, imaginary: FloatArray) {
        val size = real.size
        var target = 0
        for (index in 1 until size) {
            var bit = size shr 1
            while (target and bit != 0) {
                target = target xor bit
                bit = bit shr 1
            }
            target = target xor bit
            if (index < target) {
                val realValue = real[index]
                real[index] = real[target]
                real[target] = realValue
                val imaginaryValue = imaginary[index]
                imaginary[index] = imaginary[target]
                imaginary[target] = imaginaryValue
            }
        }

        var length = 2
        while (length <= size) {
            val angle = (-2.0 * PI / length).toFloat()
            val phaseStepReal = cos(angle)
            val phaseStepImaginary = sin(angle)
            val halfLength = length / 2
            var start = 0
            while (start < size) {
                var phaseReal = 1f
                var phaseImaginary = 0f
                for (offset in 0 until halfLength) {
                    val even = start + offset
                    val odd = even + halfLength
                    val oddReal = real[odd] * phaseReal - imaginary[odd] * phaseImaginary
                    val oddImaginary = real[odd] * phaseImaginary + imaginary[odd] * phaseReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextPhaseReal = phaseReal * phaseStepReal - phaseImaginary * phaseStepImaginary
                    phaseImaginary = phaseReal * phaseStepImaginary + phaseImaginary * phaseStepReal
                    phaseReal = nextPhaseReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}
