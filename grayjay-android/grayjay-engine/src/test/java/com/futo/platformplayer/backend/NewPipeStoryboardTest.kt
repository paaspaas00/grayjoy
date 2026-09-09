package com.futo.platformplayer.backend

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.Frameset

class NewPipeStoryboardTest {
    @Test fun convertsExplicitSpriteSheetsWithoutGuessingTheirUrls() {
        val frames = Frameset(listOf("sheet-a", "sheet-b"), 160, 90, 20, 5_000, 5, 2)
        val storyboard = with(NewPipeYoutubePlaybackBackend()) { listOf(frames).toGrayjayStoryboard() }
        val level = requireNotNull(storyboard).levels.single()
        assertEquals(listOf("sheet-a", "sheet-b"), level.sheetUrls)
        assertEquals(5_000L, level.intervalMs)
        assertEquals(20, level.frameCount)
    }

    @Test fun rejectsMalformedFramesets() {
        val frames = Frameset(emptyList(), 0, 0, 0, 0, 0, 0)
        assertNull(with(NewPipeYoutubePlaybackBackend()) { listOf(frames).toGrayjayStoryboard() })
    }
}
