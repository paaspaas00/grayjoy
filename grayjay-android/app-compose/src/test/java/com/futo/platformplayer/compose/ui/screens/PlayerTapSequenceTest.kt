package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class PlayerTapSequenceTest {
    @Test fun firstTapTogglesImmediatelySecondTapSeeksAndFurtherTapsAccumulate() {
        val taps = PlayerTapSequence()
        assertEquals(PlayerTapAction.ToggleControls, taps.released(1000, .9f, true, 300))
        assertEquals(PlayerTapAction.Seek(1, 10), taps.released(1120, .9f, true, 300))
        assertEquals(PlayerTapAction.Seek(1, 20), taps.released(1500, .9f, true, 300))
        assertEquals(PlayerTapAction.Seek(-1, 10), taps.released(1700, .1f, true, 300))
        assertEquals(PlayerTapAction.ToggleControls, taps.released(2400, .9f, true, 300))
    }
    @Test fun centerDisabledAndCancelledGesturesNeverSeek() {
        val taps = PlayerTapSequence()
        repeat(4) { assertEquals(PlayerTapAction.ToggleControls, taps.released(1000L + it * 100, .5f, true, 300)) }
        repeat(4) { assertEquals(PlayerTapAction.ToggleControls, taps.released(2000L + it * 100, .1f, false, 300)) }
        taps.released(3000, .9f, true, 300)
        taps.reset()
        assertEquals(PlayerTapAction.ToggleControls, taps.released(3100, .9f, true, 300))
    }
    @Test fun quickSeekWindowEndsAt650MsAndDoesNotExtendAcrossCenterTap() {
        val taps = PlayerTapSequence()
        taps.released(0, .1f, true, 300)
        assertEquals(PlayerTapAction.Seek(-1, 10), taps.released(100, .1f, true, 300))
        assertEquals(PlayerTapAction.ToggleControls, taps.released(200, .5f, true, 300))
        assertEquals(PlayerTapAction.ToggleControls, taps.released(300, .1f, true, 300))
        assertEquals(PlayerTapAction.Seek(-1, 10), taps.released(400, .1f, true, 300))
        assertEquals(PlayerTapAction.ToggleControls, taps.released(1051, .1f, true, 300))
    }
}
