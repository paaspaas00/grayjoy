package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLayoutTest {
    @Test
    fun phoneUsesBottomNavigation() {
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(599))
    }

    @Test
    fun tabletUsesNavigationRail() {
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(600))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(1_199))
    }

    @Test
    fun largeScreenUsesPermanentDrawer() {
        assertEquals(NavigationLayout.PermanentDrawer, navigationLayoutFor(1_200))
    }

    @Test
    fun collapsedTransitionHitSurfaceMatchesMiniPlayerHeight() {
        assertEquals(72f, transitionOverlayHeightPx(2_000f, 72f, 1f))
        assertEquals(2_000f, transitionOverlayHeightPx(2_000f, 72f, 0f))
        assertEquals(1_036f, transitionOverlayHeightPx(2_000f, 72f, 0.5f))
    }
}
