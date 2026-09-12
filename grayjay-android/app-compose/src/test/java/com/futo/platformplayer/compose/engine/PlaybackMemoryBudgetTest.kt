package com.futo.platformplayer.compose.engine

import org.junit.Assert.*
import org.junit.Test

class PlaybackMemoryBudgetTest {
    @Test fun leavesRoomForDecoderAndUiOnConstrainedHeaps() {
        val mib = 1024L * 1024
        assertEquals(16 * mib, playbackBufferBudgetBytes(128 * mib).toLong())
        assertEquals(32 * mib, playbackBufferBudgetBytes(256 * mib).toLong())
        assertEquals(48 * mib, playbackBufferBudgetBytes(512 * mib).toLong())
        assertTrue(playbackBufferBudgetBytes(Long.MAX_VALUE) > 0)
    }
}
