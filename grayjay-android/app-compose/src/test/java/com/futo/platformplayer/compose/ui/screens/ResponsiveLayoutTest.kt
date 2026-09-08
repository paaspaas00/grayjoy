package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class ResponsiveLayoutTest {
    @Test fun `compact hierarchy is limited to constrained phone viewports`() {
        assertTrue(useCompactUi(320, 569, 1f))
        assertTrue(useCompactUi(360, 720, 1.4f))
        assertFalse(useCompactUi(411, 923, 1f))
        assertFalse(useCompactUi(800, 1280, 1f))
        assertFalse(useCompactUi(1280, 800, 1f))
    }
    @Test fun `reference phone retains the established thumbnail width`() {
        assertEquals(184f, videoCardThumbnailWidth(411, compact = false), 0f)
        assertEquals(148f, videoCardThumbnailWidth(411, compact = true), 0f)
    }
    @Test fun `small screens leave room for the title instead of a fixed oversized image`() {
        assertTrue(videoCardThumbnailWidth(320, compact = false) < 125f)
        assertTrue(videoCardThumbnailWidth(360, compact = false) < 140f)
        assertTrue(videoCardThumbnailWidth(0, compact = false) > 0f)
    }
}
