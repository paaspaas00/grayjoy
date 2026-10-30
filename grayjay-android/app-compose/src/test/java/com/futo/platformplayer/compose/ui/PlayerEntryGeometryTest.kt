package com.futo.platformplayer.compose.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class PlayerEntryGeometryTest {
    @Test fun miniViewportKeepsAspectAndIsCenteredInsideTheSlot() {
        val slot = Rect(10f, 600f, 122f, 670f)
        val frame = fitPlayerViewport(slot, 16f / 9f)
        assertEquals(16f / 9f, frame.width / frame.height, .001f)
        assertEquals(slot.center, frame.center)
        assertTrue(frame.left >= slot.left && frame.right <= slot.right && frame.top >= slot.top && frame.bottom <= slot.bottom)
    }
    @Test fun allMorphSamplesKeepAspectSoTheFinalRemeasureCannotSnap() {
        for (aspect in listOf(16f / 9f, 1f, 9f / 16f, 2.35f)) {
            val end = fitPlayerViewport(Rect(0f, 800f, 112f, 870f), aspect)
            val start = Rect(0f, 100f, 400f, 100f + 400f / aspect)
            repeat(101) { i ->
                val rect = com.futo.platformplayer.compose.recognition.recognitionMorphRect(start, end, i / 100f)
                assertEquals(aspect, rect.width / rect.height, .002f)
            }
        }
    }
    @Test fun cardsOnlyAnimateTheMatchingImmediateNavigation() {
        var time = 0L
        val state = PlayerCardTransition { time }
        val source = CardMorphSource(VideoUiModel("a", "Title", "Artist", "", ""), false)
        source.capture("surface", Rect(0f, 100f, 400f, 200f))
        state.arm(source); state.begin("b"); assertFalse(state.active)
        state.arm(source); time = 2000; state.begin("a"); assertFalse(state.active)
        state.arm(source); state.begin("a"); assertTrue(state.active)
        assertFalse(state.freeze())
        state.capture("a", "surface", Rect(0f, 0f, 400f, 900f), 0f)
        state.capture("a", "art", Rect(0f, 100f, 400f, 325f), 0f)
        assertTrue(state.freeze())
        state.clear(); assertFalse(state.active)
    }
}
