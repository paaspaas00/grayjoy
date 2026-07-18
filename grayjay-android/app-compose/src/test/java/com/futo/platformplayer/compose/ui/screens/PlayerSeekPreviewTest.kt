package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.StoryboardLevelUiModel
import com.futo.platformplayer.compose.ui.StoryboardUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlayerSeekPreviewTest {
    @Test
    fun previewTimeTracksSliderProgress() {
        assertEquals(90_000L, seekPreviewPositionMs(durationMs = 180_000L, progress = 0.5f))
    }

    @Test
    fun previewTimeStaysWithinMediaBounds() {
        assertEquals(0L, seekPreviewPositionMs(durationMs = 120_000L, progress = -0.4f))
        assertEquals(120_000L, seekPreviewPositionMs(durationMs = 120_000L, progress = 1.4f))
    }

    @Test
    fun unknownDurationHasZeroPreviewTime() {
        assertEquals(0L, seekPreviewPositionMs(durationMs = 0L, progress = 0.7f))
    }

    @Test
    fun storyboardFrameSelectsSharpLevelAndCorrectSpriteCell() {
        val storyboard = StoryboardUiModel(
            levels = listOf(
                level(width = 106, columns = 10, rows = 10),
                level(width = 320, columns = 3, rows = 3),
            ),
        )

        val firstSheet = storyboard.frameAt(25_000L, targetWidthPx = 240)
        assertNotNull(firstSheet)
        requireNotNull(firstSheet)
        assertEquals(320, firstSheet.cellWidth)
        assertEquals(2, firstSheet.frameIndex)
        assertEquals(2, firstSheet.column)
        assertEquals(0, firstSheet.row)
        assertEquals("https://i.ytimg.com/L/M0.jpg", firstSheet.sheetUrl)

        val secondSheet = storyboard.frameAt(95_000L, targetWidthPx = 240)
        assertNotNull(secondSheet)
        requireNotNull(secondSheet)
        assertEquals(9, secondSheet.frameIndex)
        assertEquals(0, secondSheet.column)
        assertEquals(0, secondSheet.row)
        assertEquals("https://i.ytimg.com/L/M1.jpg", secondSheet.sheetUrl)
    }

    @Test
    fun storyboardFrameClampsToLastAvailableFrame() {
        val storyboard = StoryboardUiModel(listOf(level(width = 160, frameCount = 12)))

        assertEquals(11, requireNotNull(storyboard.frameAt(Long.MAX_VALUE, 160)).frameIndex)
    }

    private fun level(
        width: Int,
        frameCount: Int = 20,
        columns: Int = 3,
        rows: Int = 3,
    ) = StoryboardLevelUiModel(
        width = width,
        height = width * 9 / 16,
        frameCount = frameCount,
        columns = columns,
        rows = rows,
        intervalMs = 10_000L,
        sheetUrlTemplate = "https://i.ytimg.com/L/M\$M.jpg",
    )
}
