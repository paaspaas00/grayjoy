package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class FullscreenLevelGestureTest {
    @Test fun upwardsRaisesAndDownwardsLowersTheLevel() {
        assertTrue(gestureLevel(.5f, -100f, 1000f) > .5f)
        assertTrue(gestureLevel(.5f, 100f, 1000f) < .5f)
        assertEquals(.5f, gestureLevel(.5f, 100f, 0f), 0f)
    }
    @Test fun levelNeverExceedsTheRange() {
        assertEquals(1f, gestureLevel(.9f, -1000f, 100f), 0f)
        assertEquals(0f, gestureLevel(.1f, 1000f, 100f), 0f)
    }
}
