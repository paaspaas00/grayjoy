package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class ShortsFormFactorTest {
    @Test fun onlyPhonesIncludingRotatedPhonesQualify() {
        assertTrue(supportsShortsFeedPlayer(411, 923, 411))
        assertTrue(supportsShortsFeedPlayer(923, 411, 411))
        assertFalse(supportsShortsFeedPlayer(800, 1280, 800))
        assertFalse(supportsShortsFeedPlayer(400, 800, 800))
        assertFalse(supportsShortsFeedPlayer(0, 0, 0))
    }
}
