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
            miniplayerNeedsNavigationBarPadding(
                searchImeVisible = false,
                bottomNavigationProvidesInset = false,
            ),
        )
    }

    @Test
    fun keyboardAndBottomNavigationAlreadyProvideTheirOwnInset() {
        assertFalse(
            miniplayerNeedsNavigationBarPadding(
                searchImeVisible = true,
                bottomNavigationProvidesInset = false,
            ),
        )
        assertFalse(
            miniplayerNeedsNavigationBarPadding(
                searchImeVisible = false,
                bottomNavigationProvidesInset = true,
            ),
        )
    }
}
