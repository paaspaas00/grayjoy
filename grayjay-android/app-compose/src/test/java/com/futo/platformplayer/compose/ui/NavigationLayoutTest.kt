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
}
