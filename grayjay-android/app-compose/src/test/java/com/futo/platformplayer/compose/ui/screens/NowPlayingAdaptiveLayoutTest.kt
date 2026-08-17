package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingAdaptiveLayoutTest {
    @Test
    fun `side by side requires hidden drawer and enough width`() {
        assertFalse(shouldUseSideBySideNowPlaying(drawerHidden = false, availableWidthDp = 1_280f))
        assertFalse(shouldUseSideBySideNowPlaying(drawerHidden = true, availableWidthDp = 900f))
        assertTrue(shouldUseSideBySideNowPlaying(drawerHidden = true, availableWidthDp = 1_280f))
    }
}
