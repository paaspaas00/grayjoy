package com.futo.platformplayer.voice

import kotlin.math.abs
import kotlin.math.sqrt

/** Bounded capture-only fallback for devices without an exposed hardware AGC.
 * Never amplifies digital silence, never changes playback volume, and limits peaks below clipping. */
internal class VoiceInputGain(private val enabled: Boolean) {
    private var gain = 1.0
    fun process(samples: ShortArray, count: Int) {
        if (!enabled || count <= 0) return
        var energy = 0.0
        var peak = 0
        for (i in 0 until count) { val sample = samples[i].toInt(); energy += sample.toDouble() * sample; peak = maxOf(peak, abs(sample)) }
        val rms = sqrt(energy / count)
        if (rms < 64 || peak == 0) { gain += (1.0 - gain) * .2; return }
        val target = minOf((1800 / rms).coerceIn(1.0, 4.0), 28000.0 / peak).coerceAtLeast(.1)
        val previous = gain
        // Fast peak protection; smoothly raise the level of quieter speech.
        gain = if (target < gain) target else gain + (target - gain) * .65
        for (i in 0 until count) {
            val applied = if (gain < previous) gain else previous + (gain - previous) * (i + 1) / count
            samples[i] = (samples[i] * applied).toInt().coerceIn(-28000, 28000).toShort()
        }
    }
}
