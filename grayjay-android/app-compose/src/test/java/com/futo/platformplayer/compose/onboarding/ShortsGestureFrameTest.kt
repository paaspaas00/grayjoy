package com.futo.platformplayer.compose.onboarding

import org.junit.Assert.*
import org.junit.Test

class ShortsGestureFrameTest {
    @Test fun touchAndDragAreSynchronizedBeforeThePageSnapsIntoPlace() {
        val touching = shortsGestureFrame(.10f)
        assertEquals(0f, touching.pageOffset, .0001f)
        assertEquals(.75f, touching.fingerY, .0001f)
        assertEquals(1f, touching.handAlpha, .0001f)
        assertEquals(1f, touching.contactAlpha, .0001f)
        for (phase in listOf(.12f, .18f, .25f, .28f)) {
            val up = shortsGestureFrame(phase)
            assertEquals(.75f - up.fingerY, up.pageOffset, .0001f)
            val down = shortsGestureFrame(phase + .5f)
            assertEquals(1f - (down.fingerY - .24f), down.pageOffset, .0001f)
        }
        assertEquals(1f, shortsGestureFrame(.40f).pageOffset, .0001f)
        assertEquals(0f, shortsGestureFrame(.40f).handAlpha, .0001f)
        assertEquals(0f, shortsGestureFrame(.90f).pageOffset, .0001f)
        assertEquals(0f, shortsGestureFrame(.90f).handAlpha, .0001f)
        assertEquals(shortsGestureFrame(0f), shortsGestureFrame(1f))
    }

    @Test fun everyFrameIsBoundedAndBothSwipesAreMonotonic() {
        var previous = 0f
        for (index in 0..10_000) {
            val progress = index / 10_000f
            val frame = shortsGestureFrame(progress)
            assertTrue(frame.pageOffset in 0f..1f)
            assertTrue(frame.fingerY in .2399f.. .7501f)
            assertTrue(frame.handAlpha in 0f..1f)
            assertTrue(frame.contactAlpha in 0f..1f)
            if (progress <= .5f) assertTrue(frame.pageOffset + .0001f >= previous)
            else assertTrue(frame.pageOffset <= previous + .0001f)
            previous = frame.pageOffset
        }
        assertEquals(shortsGestureFrame(0f), shortsGestureFrame(Float.NaN))
        assertEquals(shortsGestureFrame(0f), shortsGestureFrame(Float.POSITIVE_INFINITY))
    }
}
