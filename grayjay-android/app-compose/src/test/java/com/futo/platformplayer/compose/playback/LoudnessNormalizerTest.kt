package com.futo.platformplayer.compose.playback

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class LoudnessNormalizerTest {
    @Test fun kWeightedReferenceToneMatchesMonoAndStereoAtCommonSampleRates() {
        for (rate in listOf(44_100, 48_000, 96_000)) for (channels in 1..2) {
            val meter = StreamingLoudnessMeter(rate, channels)
            val frame = DoubleArray(channels)
            repeat(rate * 3) { i ->
                frame.fill(.1 * sin(2 * PI * 1_000 * i / rate))
                meter.addFrame(frame)
            }
            // A 1 kHz sine at -20 dBFS peak: -23 LUFS mono, -20 LUFS dual-mono.
            assertEquals("rate=$rate channels=$channels", if (channels == 1) -23.0 else -20.0,
                meter.integratedLufs!!, .16)
        }
    }

    @Test fun loudAndQuietRecordingsConvergeToMinus23WithoutChangingChannelBalance() {
        val rate = 48_000
        for (amplitude in listOf(.6, .1, .03)) {
            val normalizer = LoudnessNormalizer(rate, 2)
            val outputMeter = StreamingLoudnessMeter(rate, 2)
            val frame = DoubleArray(2)
            repeat(rate * 18) { i ->
                frame[0] = amplitude * sin(2 * PI * 1_000 * i / rate)
                frame[1] = frame[0] * .5
                normalizer.processFrame(frame)
                assertEquals(frame[0] * .5, frame[1], 1e-12)
                if (i >= rate * 13) outputMeter.addFrame(frame)
            }
            assertEquals("amplitude=$amplitude", -23.0, outputMeter.integratedLufs!!, .3)
        }
    }

    @Test fun silenceAndSubGateNoiseAreNotBoostedAndLfeIsNotIncludedInLoudness() {
        val normalizer = LoudnessNormalizer(8_000, 1)
        val frame = doubleArrayOf(0.0)
        repeat(80_000) { i ->
            val input = if (i < 40_000) 0.0 else 1e-7 * sin(i.toDouble())
            frame[0] = input
            normalizer.processFrame(frame)
            assertEquals(input, frame[0], 1e-15)
        }
        assertNull(normalizer.measuredLufs)
        val meter = StreamingLoudnessMeter(48_000, 6)
        val surround = DoubleArray(6)
        repeat(48_000) { i -> surround[3] = sin(i.toDouble()); meter.addFrame(surround) }
        assertNull(meter.integratedLufs)
    }

    @Test fun relativeGateExcludesQuietTailAndAbsoluteGateExcludesDigitalSilence() {
        val rate = 8_000
        val meter = StreamingLoudnessMeter(rate, 2)
        val frame = DoubleArray(2)
        repeat(rate * 5) { i -> frame.fill(.2 * sin(2 * PI * 1_000 * i / rate)); meter.addFrame(frame) }
        val before = meter.integratedLufs!!
        repeat(rate * 20) { i -> frame.fill(.0001 * sin(2 * PI * 1_000 * i / rate)); meter.addFrame(frame) }
        assertEquals(before, meter.integratedLufs!!, .25)
    }

    @Test fun malformedAndExtremeSamplesRemainFiniteAndBelowPeakCeiling() {
        val random = Random(8745)
        for (channels in listOf(1, 2, 4, 6, 8)) {
            val normalizer = LoudnessNormalizer(48_000, channels)
            val frame = DoubleArray(channels)
            repeat(50_000) { index ->
                for (channel in frame.indices) frame[channel] = when (index % 97) {
                    0 -> Double.NaN
                    1 -> Double.POSITIVE_INFINITY
                    2 -> Double.NEGATIVE_INFINITY
                    else -> random.nextDouble(-3.0, 3.0)
                }
                normalizer.processFrame(frame)
                for (sample in frame) assertTrue(sample.isFinite() && abs(sample) <= LoudnessNormalizer.SAMPLE_PEAK_CEILING)
            }
        }
    }
}
