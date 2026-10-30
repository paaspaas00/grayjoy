package com.futo.platformplayer.compose.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

@UnstableApi
internal class LoudnessNormalizationAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false
    @Volatile var targetLufs: Int = -32
    private val streamGeneration = AtomicInteger()
    private var appliedGeneration = -1
    private var normalizer: LoudnessNormalizer? = null
    private var normalizerFormat = AudioFormat.NOT_SET
    private var frame = DoubleArray(0)
    internal val isDspAllocated get() = normalizer != null

    fun resetMeasurement() { streamGeneration.incrementAndGet() }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (enabled && inputAudioFormat.sampleRate in 8_000..192_000 && inputAudioFormat.channelCount in 1..8 &&
            inputAudioFormat.encoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) inputAudioFormat
        else AudioFormat.NOT_SET

    override fun onFlush() {
        // A seek within the same recording must not jump back to its unnormalized volume.
        if (enabled && isActive && inputAudioFormat == normalizerFormat) return
        clearDsp()
    }

    private fun clearDsp() {
        normalizer = null
        normalizerFormat = AudioFormat.NOT_SET
        frame = DoubleArray(0)
        appliedGeneration = -1
    }

    override fun onReset() = clearDsp()

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // The sink excludes this processor altogether when disabled. This branch also safely
        // bypasses any old buffer during the short renderer reconfiguration after a toggle.
        if (!enabled || !isActive) {
            clearDsp()
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        val generation = streamGeneration.get()
        if (normalizer == null || appliedGeneration != generation) {
            normalizer = LoudnessNormalizer(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, targetLufs.toDouble())
            normalizerFormat = inputAudioFormat
            frame = DoubleArray(inputAudioFormat.channelCount)
            appliedGeneration = generation
        }
        val floatPcm = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        inputBuffer.order(ByteOrder.nativeOrder())
        val frameBytes = frame.size * if (floatPcm) 4 else 2
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val activeNormalizer = normalizer!!
        activeNormalizer.targetLufs = targetLufs.toDouble()
        while (inputBuffer.remaining() >= frameBytes) {
            for (channel in frame.indices) frame[channel] =
                if (floatPcm) inputBuffer.float.toDouble() else inputBuffer.short / 32768.0
            activeNormalizer.processFrame(frame)
            for (sample in frame) {
                if (floatPcm) output.putFloat(sample.toFloat())
                else output.putShort((sample * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        // Decoders provide complete PCM frames. Preserve malformed trailing bytes without
        // reading beyond the buffer or turning a playback error into a process crash.
        output.put(inputBuffer)
        output.flip()
    }
}
