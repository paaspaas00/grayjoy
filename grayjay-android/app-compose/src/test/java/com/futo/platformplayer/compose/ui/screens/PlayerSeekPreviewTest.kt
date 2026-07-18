package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSeekPreviewTest {
    @Test
    fun previewTimeTracksSliderProgress() {
        assertEquals(90_000L, seekPreviewPositionMs(durationMs = 180_000L, progress = 0.5f))
    }

    @Test
    fun previewTimeStaysWithinMediaBounds() {
        assertEquals(0L, seekPreviewPositionMs(durationMs = 120_000L, progress = -0.4f))
        assertEquals(120_000L, seekPreviewPositionMs(durationMs = 120_000L, progress = 1.4f))
    }

    @Test
    fun unknownDurationHasZeroPreviewTime() {
        assertEquals(0L, seekPreviewPositionMs(durationMs = 0L, progress = 0.7f))
    }
}
