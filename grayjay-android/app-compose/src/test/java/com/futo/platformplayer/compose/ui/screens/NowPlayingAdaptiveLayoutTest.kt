package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingAdaptiveLayoutTest {
    @Test
    fun `side by side requires hidden drawer and enough width`() {
        assertFalse(shouldUseSideBySideNowPlaying(drawerHidden = false, availableWidthDp = 1_280f))
        assertFalse(shouldUseSideBySideNowPlaying(drawerHidden = true, availableWidthDp = 900f))
        assertTrue(shouldUseSideBySideNowPlaying(drawerHidden = true, availableWidthDp = 1_280f))
    }

    @Test
    fun `side panel receives every block that does not fit under player`() {
        assertEquals(
            SideBySideNowPlayingPlacement(true, true, true),
            sideBySideNowPlayingPlacement(availableWidthDp = 1_280f, availableHeightDp = 800f),
        )
        assertEquals(
            SideBySideNowPlayingPlacement(true, true, false),
            sideBySideNowPlayingPlacement(availableWidthDp = 1_280f, availableHeightDp = 700f),
        )
        assertEquals(
            SideBySideNowPlayingPlacement(true, false, false),
            sideBySideNowPlayingPlacement(availableWidthDp = 1_280f, availableHeightDp = 600f),
        )
        assertEquals(
            SideBySideNowPlayingPlacement(false, false, false),
            sideBySideNowPlayingPlacement(availableWidthDp = 1_280f, availableHeightDp = 500f),
        )
    }
}
