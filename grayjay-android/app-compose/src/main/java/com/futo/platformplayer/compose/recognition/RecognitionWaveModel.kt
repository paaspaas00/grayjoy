package com.futo.platformplayer.compose.recognition

import kotlin.math.*

internal data class RecognitionAudioFrame(val loudness: Float, val bands: FloatArray = FloatArray(15) { 1f }) {
    companion object { val Silence = RecognitionAudioFrame(0f, FloatArray(15)) }
}

/** Simultaneous levels, not a scrolling sample history. No audio samples are retained. */
internal class RecognitionWaveModel(val count: Int = 15) {
    init { require(count in 5..31) }
    val bars = FloatArray(count)
    val cloudRadii = FloatArray(8)
    private val previousShape = FloatArray(8)
    private val nextShape = FloatArray(8)
    private val targets = FloatArray(count)
    private var shapeTime = .2f
    private var rotation = 0
    val blobX get() = .5f
    val blobEnergy get() = cloudRadii.maxOrNull() ?: 0f

    fun advance(frame: RecognitionAudioFrame, seconds: Float, listening: Boolean = true) {
        val dt = seconds.takeIf(Float::isFinite)?.coerceIn(0f, .1f) ?: 0f
        val energy = if (listening) frame.loudness.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f else 0f
        val smoothing = 1f - exp(-dt * 7.67f)
        for (i in bars.indices) {
            val band = frame.bands.getOrElse(i) { 0f }.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
            targets[i] = if (i == 0 || i == count - 1) 0f else band * energy
            bars[i] += (targets[i] - bars[i]) * smoothing
            if (bars[i] < .002f) bars[i] = 0f
        }
        shapeTime += dt
        if (shapeTime >= .2f) {
            nextShape.copyInto(previousShape)
            val from = (count / 2 - 3).coerceAtLeast(0)
            val to = count / 2
            val middle = (from..to).sumOf { targets[it].toDouble() }.toFloat() / (to - from + 1)
            for (i in nextShape.indices) {
                val sample = targets[(i + rotation) % minOf(8, count)]
                nextShape[i] = ((middle + sample) * .45f).coerceIn(0f, .9f)
            }
            rotation = (rotation + 5) % 8
            shapeTime %= .2f
        }
        val progress = (shapeTime / .2f).coerceIn(0f, 1f)
        for (i in cloudRadii.indices) cloudRadii[i] = previousShape[i] + (nextShape[i] - previousShape[i]) * progress
    }
}

internal fun recognitionDisplayLevel(rms: Double): Float {
    if (!rms.isFinite() || rms <= 0.0) return 0f
    val db = 20 * log10((rms / 32768).coerceAtLeast(1e-6))
    return ((db + 58) / 40).toFloat().coerceIn(0f, 1f)
}
