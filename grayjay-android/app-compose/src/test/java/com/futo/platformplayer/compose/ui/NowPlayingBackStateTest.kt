package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class NowPlayingBackStateTest {
    private val page = BrowsePage(BrowseRoute(GrayjayDestination.Home.name))
    @Test fun aNewGestureCannotBeResetByAnOlderCancelledGesture() {
        val state = NowPlayingBackState()
        val first = state.begin(page)
        state.move(first, .6f)
        val second = state.begin(page)
        state.move(second, .4f, true)
        state.clear(first); state.move(first, 0f)
        assertTrue(state.active); assertEquals(.4f, state.progress); assertTrue(state.fromRight)
        state.clear(second); assertFalse(state.active); assertNull(state.target)
    }
    @Test fun malformedOrOutOfRangeProgressCannotGenerateInvalidLayerGeometry() {
        val state = NowPlayingBackState(); val token = state.begin(page)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1e20f, -1f, 0f, .45f, 1f, 1e20f)) {
            state.move(token, value)
            assertTrue(state.progress in 0f..1f)
            assertTrue(backDepartureScale(value) in .92f..1f)
            assertTrue(backDepartureAlpha(value) in 0f..1f)
        }
        assertEquals(1f, backDepartureScale(0f)); assertEquals(.92f, backDepartureScale(1f))
        assertEquals(1f, backDepartureAlpha(0f)); assertEquals(0f, backDepartureAlpha(1f))
    }
}
