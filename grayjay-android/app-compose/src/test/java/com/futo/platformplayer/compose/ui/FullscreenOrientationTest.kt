package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenOrientationTest {
    @Test
    fun `youtube shorts url uses portrait fullscreen before dimensions load`() {
        val video = testVideo(id = "https://www.youtube.com/shorts/abc123")

        assertTrue(usePortraitPlayerFullscreen(video, PlaybackUiState()))
    }

    @Test
    fun `decoded portrait video uses portrait fullscreen`() {
        val playback = PlaybackUiState(
            currentVideoId = "video",
            currentVideoWidth = 720,
            currentVideoHeight = 1280,
        )

        assertTrue(usePortraitPlayerFullscreen(testVideo(), playback))
    }

    @Test
    fun `decoded widescreen video uses landscape fullscreen`() {
        val playback = PlaybackUiState(
            currentVideoId = "video",
            currentVideoWidth = 1920,
            currentVideoHeight = 1080,
        )

        assertFalse(usePortraitPlayerFullscreen(testVideo(), playback))
    }

    @Test
    fun `dimensions from the previous video do not affect fullscreen orientation`() {
        val playback = PlaybackUiState(
            currentVideoId = "previous-video",
            currentVideoWidth = 720,
            currentVideoHeight = 1280,
        )

        assertFalse(usePortraitPlayerFullscreen(testVideo(), playback))
    }

    private fun testVideo(id: String = "video") = VideoUiModel(
        id = id,
        title = "Video",
        creator = "Creator",
        metadata = "",
        duration = "1:00",
    )
}
