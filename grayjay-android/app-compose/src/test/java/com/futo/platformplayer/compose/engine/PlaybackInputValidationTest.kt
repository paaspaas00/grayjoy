package com.futo.platformplayer.compose.engine

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Random

class PlaybackInputValidationTest {
    @Test fun backgroundResolutionCannotResetPlaybackRecovery() {
        assertEquals(
            listOf(EngineResolvePriority.UserPlayback),
            EngineResolvePriority.entries.filter { it.tracksPlaybackRecovery },
        )
    }

    @Test fun speedsAreFiniteAndWithinMedia3Limits() {
        assertEquals(1f, normalizedPlaybackSpeed(Float.NaN))
        assertEquals(1f, normalizedPlaybackSpeed(Float.POSITIVE_INFINITY))
        assertEquals(1f, normalizedPlaybackSpeed(Float.NEGATIVE_INFINITY))
        assertEquals(0.25f, normalizedPlaybackSpeed(-1f))
        assertEquals(3f, normalizedPlaybackSpeed(10f))
        val random = Random(24926L)
        repeat(10_000) {
            val result = normalizedPlaybackSpeed(Float.fromBits(random.nextInt()))
            assertTrue(result.isFinite() && result in 0.25f..3f)
        }
    }

    @Test fun seekOffsetsSaturateAtBothEnds() {
        assertEquals(Long.MAX_VALUE, clampedSeekPosition(1_000L, Long.MAX_VALUE, null))
        assertEquals(0L, clampedSeekPosition(1_000L, Long.MIN_VALUE, null))
        assertEquals(10_000L, clampedSeekPosition(1_000L, Long.MAX_VALUE, 10_000L))
        assertEquals(0L, clampedSeekPosition(-100L, 0L, null))
    }

    @Test fun seededSeekOffsetsMatchUnboundedIntegerReference() {
        val random = Random(20260924L)
        repeat(10_000) {
            val current = random.nextLong()
            val delta = random.nextLong()
            val duration = if (random.nextBoolean()) random.nextLong() and Long.MAX_VALUE else null
            val ceiling = BigInteger.valueOf(duration ?: Long.MAX_VALUE)
            val expected = BigInteger.valueOf(current.coerceAtLeast(0L))
                .add(BigInteger.valueOf(delta)).max(BigInteger.ZERO).min(ceiling).toLong()
            assertEquals(expected, clampedSeekPosition(current, delta, duration))
        }
    }
}
