package com.futo.platformplayer.compose.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.futo.platformplayer.compose.ui.screens.VideoFrameReadiness
import com.futo.platformplayer.compose.ui.screens.dialogChoiceShape
import org.junit.Assert.*
import org.junit.Test

class VideoFrameAndChoiceShapeTest {
    @Test fun thePreviousItemAndItsLateFrameCannotUnlockTheNewSurface() {
        val state = VideoFrameReadiness("new")
        state.mediaItemChanged("old"); state.renderedFrame("old"); assertFalse(state.ready)
        state.mediaItemChanged("new"); assertFalse(state.ready)
        state.renderedFrame("old"); assertFalse(state.ready)
        state.renderedFrame("new"); assertTrue(state.ready)
    }
    @Test fun stoppingOrMovingToAnotherItemImmediatelyInvalidatesItsFrame() {
        val state = VideoFrameReadiness("new")
        state.mediaItemChanged("new"); state.renderedFrame("new"); assertTrue(state.ready)
        state.mediaItemChanged("other"); assertFalse(state.ready)
        state.renderedFrame("new"); assertFalse(state.ready)
        state.mediaItemChanged(""); assertFalse(state.ready)
    }
    @Test fun repeatOfTheSameVideoDoesNotFlashThePlaceholder() {
        val state = VideoFrameReadiness("same")
        state.mediaItemChanged("same"); state.renderedFrame("same")
        state.mediaItemChanged("same"); assertTrue(state.ready)
    }
    @Test fun groupCornersAreOnlyLargeAtTheTwoOuterEnds() {
        val size = Size(300f, 80f); val density = Density(1f)
        val first = dialogChoiceShape(0, 3); val middle = dialogChoiceShape(1, 3); val last = dialogChoiceShape(2, 3)
        assertEquals(16f, first.topStart.toPx(size, density)); assertEquals(4f, first.bottomStart.toPx(size, density))
        assertEquals(4f, middle.topStart.toPx(size, density)); assertEquals(4f, middle.bottomStart.toPx(size, density))
        assertEquals(4f, last.topStart.toPx(size, density)); assertEquals(16f, last.bottomStart.toPx(size, density))
        val only = dialogChoiceShape(0, 1)
        assertEquals(16f, only.topStart.toPx(size, density)); assertEquals(16f, only.bottomStart.toPx(size, density))
    }
}
