package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePlaybackPromptTest {
    @Test
    fun `partially watched videos offer their saved position`() {
        val video = video(watchProgress = 0.42f)

        assertEquals(0.42f, video.resumePositionFraction())
    }

    @Test
    fun `fresh completed and live videos do not offer resume`() {
        assertNull(video(watchProgress = 0f).resumePositionFraction())
        assertNull(video(watchProgress = 0.001f).resumePositionFraction())
        assertNull(video(watchProgress = 0.95f).resumePositionFraction())
        assertNull(video(watchProgress = 0.42f, isLive = true).resumePositionFraction())
    }

    private fun video(watchProgress: Float, isLive: Boolean = false) = VideoUiModel(
        id = "video",
        title = "Video",
        creator = "Creator",
        metadata = "",
        duration = "10:00",
        watchProgress = watchProgress,
        isLive = isLive,
    )
}
