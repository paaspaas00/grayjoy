package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingDownloadChipStateTest {
    @Test
    fun audioDownloadDrivesButtonProgress() {
        val state = nowPlayingDownloadChipState(
            download(
                mediaType = DownloadMediaType.Audio,
                progress = 0.42f,
                active = setOf(DownloadMediaType.Audio),
            ),
        )

        assertTrue(state.isActive)
        assertEquals(0.42f, state.progress, 0.0001f)
        assertFalse(state.isComplete)
    }

    @Test
    fun completedAudioFillsButtonAndMarksItAvailableOffline() {
        val state = nowPlayingDownloadChipState(
            download(
                mediaType = DownloadMediaType.Audio,
                status = DownloadStatus.Completed,
                completed = setOf(DownloadMediaType.Audio),
            ),
        )

        assertTrue(state.audioComplete)
        assertTrue(state.isComplete)
        assertEquals(1f, state.progress, 0.0001f)
    }

    @Test
    fun activeAudioProgressWinsOverAnExistingCompletedVideo() {
        val state = nowPlayingDownloadChipState(
            download(
                mediaType = DownloadMediaType.Audio,
                progress = 0.25f,
                active = setOf(DownloadMediaType.Audio),
                completed = setOf(DownloadMediaType.Video),
            ),
        )

        assertTrue(state.videoComplete)
        assertTrue(state.isActive)
        assertEquals(0.25f, state.progress, 0.0001f)
    }

    private fun download(
        mediaType: DownloadMediaType,
        status: DownloadStatus = DownloadStatus.Downloading,
        progress: Float? = null,
        active: Set<DownloadMediaType> = emptySet(),
        completed: Set<DownloadMediaType> = emptySet(),
    ) = DownloadUiModel(
        profileId = "main",
        videoId = "video",
        mediaType = mediaType,
        status = status,
        progress = progress,
        activeMediaTypes = active,
        completedMediaTypes = completed,
    )
}
