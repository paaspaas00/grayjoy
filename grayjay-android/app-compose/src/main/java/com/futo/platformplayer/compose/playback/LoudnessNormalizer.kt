package com.futo.platformplayer.compose.playback

import kotlin.math.*

/** Streaming BS.1770 K-weighted measurement, with 400 ms blocks and 75% overlap.
 * A fixed 0.1 LU histogram bounds memory even for long live streams. This is an online
 * estimate, not an offline whole-programme R128 certification or a true-peak meter.
 */
internal class StreamingLoudnessMeter(val sampleRate: Int, val channels: Int) {
    init { require(sampleRate in 8_000..192_000 && channels in 1..8) }
    private val shelf = Array(channels) { KWeightingFilter.shelf(sampleRate) }
    private val highPass = Array(channels) { KWeightingFilter.highPass(sampleRate) }
    private val weights = DoubleArray(channels) { channel ->
        when {
            channels >= 6 && channel == 3 -> 0.0 // Android PCM order: L, R, C, LFE, surrounds.
            channels == 4 && channel >= 2 -> 1.41
            channels == 5 && channel >= 3 -> 1.41
            channels >= 6 && channel >= 4 -> 1.41
            else -> 1.0
        }
    }
    private val hopFrames = sampleRate / 10
    private val hops = DoubleArray(4)
    private val binEnergy = DoubleArray(1_000)
    private val binCount = LongArray(1_000)
    private var frames = 0
    private var hopEnergy = 0.0
    private var hopIndex = 0
    private var completedHops = 0
    private var totalEnergy = 0.0
    private var totalBlocks = 0L
    var integratedLufs: Double? = null
        private set

    fun addFrame(samples: DoubleArray): Boolean {
        for (channel in 0 until channels) {
            val weighted = highPass[channel].process(shelf[channel].process(samples[channel]))
            hopEnergy += weighted * weighted * weights[channel]
        }
        if (++frames < hopFrames) return false
        frames = 0
        hops[hopIndex] = hopEnergy
        hopIndex = (hopIndex + 1) % hops.size
        hopEnergy = 0.0
        completedHops = (completedHops + 1).coerceAtMost(4)
        if (completedHops < 4) return false
        val energy = hops.sum() / (4 * hopFrames)
        val loudness = if (energy > 0.0) -0.691 + 10.0 * log10(energy) else Double.NEGATIVE_INFINITY
        if (!loudness.isFinite() || loudness < -70.0) return false
        val index = ((loudness + 70.0) * 10).toInt().coerceIn(binCount.indices)
        binEnergy[index] += energy
        binCount[index]++
        totalEnergy += energy
        totalBlocks++
        val relativeGate = (-0.691 + 10.0 * log10(totalEnergy / totalBlocks) - 10.0).coerceAtLeast(-70.0)
        val startBin = ceil((relativeGate + 70.0) * 10.0).toInt().coerceIn(binCount.indices)
        var gatedEnergy = 0.0
        var gatedCount = 0L
        for (bin in startBin until binCount.size) {
            gatedEnergy += binEnergy[bin]
            gatedCount += binCount[bin]
        }
        if (gatedCount > 0 && gatedEnergy > 0.0)
            integratedLufs = -0.691 + 10.0 * log10(gatedEnergy / gatedCount)
        return true
    }
}

/** Linked-channel gain avoids stereo-image shifts; silence is never used to increase gain. */
internal class LoudnessNormalizer(sampleRate: Int, channels: Int) {
    private val meter = StreamingLoudnessMeter(sampleRate, channels)
    private val attenuationStep = 1.0 - exp(-1.0 / (sampleRate * .15))
    private val boostStep = 1.0 - exp(-1.0 / (sampleRate * 2.0))
    private val limiterRelease = 1.0 - exp(-1.0 / (sampleRate * .1))
    private var targetGain = 1.0
    private var smoothGain = 1.0
    private var limitedGain = 1.0
    val measuredLufs get() = meter.integratedLufs

    fun processFrame(samples: DoubleArray) {
        var peak = 0.0
        for (channel in samples.indices) {
            val sample = samples[channel]
            samples[channel] = if (sample.isFinite()) sample.coerceIn(-1.0, 1.0) else 0.0
            peak = max(peak, abs(samples[channel]))
        }
        if (meter.addFrame(samples)) meter.integratedLufs?.let { loudness ->
            // Limit amplification of quiet recordings/noise. This can intentionally prevent
            // reaching the target on very quiet or highly dynamic material.
            targetGain = 10.0.pow((TARGET_LUFS - loudness).coerceIn(-40.0, 12.0) / 20.0)
        }
        smoothGain += (targetGain - smoothGain) * if (targetGain < smoothGain) attenuationStep else boostStep
        val safeGain = if (peak > 0.0) min(smoothGain, SAMPLE_PEAK_CEILING / peak) else smoothGain
        limitedGain = if (safeGain < limitedGain) safeGain else min(safeGain, limitedGain + limiterRelease * (safeGain - limitedGain))
        for (channel in samples.indices) samples[channel] =
            (samples[channel] * limitedGain).coerceIn(-SAMPLE_PEAK_CEILING, SAMPLE_PEAK_CEILING)
    }

    companion object {
        const val TARGET_LUFS = -23.0
        // Sample-peak protection with 2 dB of headroom; not advertised as a true-peak limiter.
        val SAMPLE_PEAK_CEILING = 10.0.pow(-2.0 / 20.0)
    }
}

private class KWeightingFilter(
    val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0
    fun process(input: Double): Double {
        val output = b0 * input + z1
        z1 = b1 * input - a1 * output + z2
        z2 = b2 * input - a2 * output
        // Avoid denormal work during long digital silence.
        if (abs(z1) < 1e-30) z1 = 0.0
        if (abs(z2) < 1e-30) z2 = 0.0
        return output
    }
    companion object {
        // Bilinear K-weighting parameters reproduce the BS.1770 coefficients at 48 kHz
        // and preserve their response at the actual decoded PCM rate.
        fun shelf(rate: Int): KWeightingFilter {
            val k = tan(PI * 1681.974450955533 / rate)
            val q = 0.7071752369554196
            val vh = 10.0.pow(3.999843853973347 / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1.0 + k / q + k * k
            return KWeightingFilter((vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0,
                (vh - vb * k / q + k * k) / a0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
        }
        fun highPass(rate: Int): KWeightingFilter {
            val k = tan(PI * 38.13547087602444 / rate)
            val a0 = 1 + k / 0.5003270373238773 + k * k
            return KWeightingFilter(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0,
                (1 - k / 0.5003270373238773 + k * k) / a0)
        }
    }
}
