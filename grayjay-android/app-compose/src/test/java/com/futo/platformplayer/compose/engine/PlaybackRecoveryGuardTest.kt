package com.futo.platformplayer.compose.engine

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryGuardTest {
    @Test fun repeatedTruncationAtSameTimestampEscalates() {
        val guard = PlaybackRecoveryGuard()
        assertFalse(guard.recordFailure("video", 678841, true))
        assertTrue(guard.recordFailure("video", 678841, true))
        assertTrue(guard.recordFailure("video", 679200, true))
    }

    @Test fun actualProgressOrNewVideoDoesNotEscalateOldErrors() {
        val guard = PlaybackRecoveryGuard()
        guard.recordFailure("video", 678841, true)
        assertFalse(guard.recordFailure("video", 710000, true))
        assertFalse(guard.recordFailure("other", 710000, true))
    }

    @Test fun offlineFailuresWaitForNetworkWithoutExhaustingFallback() {
        val guard = PlaybackRecoveryGuard()
        repeat(20) { assertFalse(guard.recordFailure("video", 678841, false)) }
        assertFalse(guard.recordFailure("video", 678841, true))
    }

    @Test fun extremePositionsAndRepeatedFailuresCannotOverflow() {
        val guard = PlaybackRecoveryGuard()
        assertFalse(guard.recordFailure("video", Long.MAX_VALUE, true))
        repeat(10_000) { assertTrue(guard.recordFailure("video", Long.MAX_VALUE, true)) }
        assertFalse(guard.recordFailure("video", Long.MIN_VALUE, true))
        assertTrue(guard.recordFailure("video", 0, true))
        guard.reset()
        assertFalse(guard.recordFailure("video", 0, true))
    }

    @Test fun seededPositionsEscalateOnlyInsideTheSameFailureWindow() {
        val random = java.util.Random(20260924L)
        repeat(10_000) {
            val start = (random.nextLong() and Long.MAX_VALUE).coerceAtMost(Long.MAX_VALUE - 10_000)
            val delta = random.nextInt(10_001).toLong()
            val guard = PlaybackRecoveryGuard()
            assertFalse(guard.recordFailure("video", start, true))
            assertEquals(delta <= 5_000, guard.recordFailure("video", start + delta, true))
        }
    }
}
