package com.futo.platformplayer.compose.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePlayerLayoutTest {
    @Test
    fun playerCoordinatesAreRelativeToNavigationRailContent() {
        val local = playerBoundsInsideScaffold(
            boundsInRoot = Rect(left = 104f, top = 88f, right = 904f, bottom = 538f),
            scaffoldLeftInRoot = 80f,
            scaffoldTopInRoot = 24f,
        )

        assertEquals(Rect(left = 24f, top = 64f, right = 824f, bottom = 514f), local)
    }

    @Test
    fun railAndDrawerMiniplayerReserveAndroidNavigationBar() {
        assertTrue(
            scaffoldBottomBarNeedsNavigationBarPadding(
                bottomNavigationProvidesInset = false,
            ),
        )
    }

    @Test
    fun bottomNavigationAlreadyProvidesItsOwnInset() {
        assertFalse(
            scaffoldBottomBarNeedsNavigationBarPadding(
                bottomNavigationProvidesInset = true,
            ),
        )
    }

    @Test
    fun searchMiniplayerFollowsLargestKeyboardOrNavigationInset() {
        assertEquals(820, searchMiniplayerBottomInsetPx(820, 72, 240))
        assertEquals(240, searchMiniplayerBottomInsetPx(0, 72, 240))
        assertEquals(72, searchMiniplayerBottomInsetPx(0, 72, 0))
    }

    @Test
    fun automaticRotationFullscreenIsLimitedToCompactViewports() {
        assertTrue(automaticFullscreenAllowedForViewport(360, 800))
        assertTrue(automaticFullscreenAllowedForViewport(800, 360))
        assertFalse(automaticFullscreenAllowedForViewport(800, 1_280))
        assertFalse(automaticFullscreenAllowedForViewport(1_280, 800))
    }
}
