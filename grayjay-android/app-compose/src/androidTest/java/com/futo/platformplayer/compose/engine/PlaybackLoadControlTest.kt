package com.futo.platformplayer.compose.engine

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.upstream.Allocation
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackLoadControlTest {
    @Test fun highBitrateStreamStopsLoadingAtHeapBudgetBeforeFiftySeconds() {
        val heap = 256L * 1024 * 1024
        val budget = playbackBufferBudgetBytes(heap)
        val control = createPlaybackLoadControl(heap)
        val id = PlayerId("memory-budget-test")
        val timeline = SinglePeriodTimeline(
            120_000_000, true, false, false, null, MediaItem.fromUri("https://example.org/test.mp4"),
        )
        val parameters = LoadControl.Parameters(
            id, timeline, MediaPeriodId(timeline.getUidOfPeriod(0)), 0, 2_000_000, 1f,
            true, false, C.TIME_UNSET, C.TIME_UNSET,
        )
        val allocations = mutableListOf<Allocation>()
        control.onPrepared(id)
        val allocator = control.getAllocator(id)
        try {
            assertTrue(control.shouldContinueLoading(parameters))
            while (allocator.totalBytesAllocated < budget) allocations += allocator.allocate()
            // Only 2 seconds buffered: this must still stop to leave heap for the decoder.
            assertFalse(control.shouldContinueLoading(parameters))
            assertTrue(control.shouldStartPlayback(parameters))
            assertTrue(allocator.totalBytesAllocated <= budget + 65_536)
            allocations.forEach(allocator::release)
            allocations.clear()
            assertEquals(0, allocator.totalBytesAllocated)
            control.onStopped(id)
            allocator.trim()
        } finally {
            allocations.forEach(allocator::release)
            control.onReleased(id)
        }
    }
}
