package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingDownloadedTitleTest {
    @Test
    fun `title follows completed download state instead of playback snapshot`() {
        assertFalse(hasNowPlayingDownload(null))
        assertFalse(hasNowPlayingDownload(download(status = DownloadStatus.Downloading)))
        assertTrue(hasNowPlayingDownload(download(status = DownloadStatus.Completed)))
        assertTrue(
            hasNowPlayingDownload(
                download(
                    status = DownloadStatus.Failed,
                    completed = setOf(DownloadMediaType.Audio),
                ),
            ),
        )
    }

    @Test
    fun `title icon disappears immediately when the only saved format is removed`() {
        assertFalse(
            hasNowPlayingDownload(
                download(
                    status = DownloadStatus.Removing,
                    mediaType = DownloadMediaType.Video,
                    completed = setOf(DownloadMediaType.Video),
                ),
            ),
        )
    }

    @Test
    fun `title icon remains when another saved format survives removal`() {
        assertTrue(
            hasNowPlayingDownload(
                download(
                    status = DownloadStatus.Removing,
                    mediaType = DownloadMediaType.Video,
                    completed = setOf(DownloadMediaType.Video, DownloadMediaType.Audio),
                ),
            ),
        )
    }

    private fun download(
        status: DownloadStatus,
        mediaType: DownloadMediaType = DownloadMediaType.Video,
        completed: Set<DownloadMediaType> = emptySet(),
    ) = DownloadUiModel(
        profileId = "main",
        videoId = "video",
        mediaType = mediaType,
        status = status,
        completedMediaTypes = completed,
    )
}
