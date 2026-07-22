package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDownloadExportTest {
    @Test
    fun `a media type is available only when every selected video has it completed`() {
        val downloads = mapOf(
            "both" to completed("both", DownloadMediaType.Video, DownloadMediaType.Audio),
            "video-only" to completed("video-only", DownloadMediaType.Video),
        )

        val availability = downloadExportAvailability(
            selectedVideoIds = listOf("both", "video-only"),
            downloads = downloads,
        )

        assertTrue(availability.canExportVideo)
        assertFalse(availability.canExportAudio)
    }

    @Test
    fun `nothing can be exported without a selection or with an incomplete item`() {
        val incomplete = DownloadUiModel(
            profileId = "main",
            videoId = "pending",
            mediaType = DownloadMediaType.Audio,
            status = DownloadStatus.Downloading,
            activeMediaTypes = setOf(DownloadMediaType.Audio),
        )

        assertFalse(downloadExportAvailability(emptyList(), emptyMap()).canExportVideo)
        assertFalse(
            downloadExportAvailability(listOf("pending"), mapOf("pending" to incomplete))
                .canExportAudio,
        )
    }

    private fun completed(
        videoId: String,
        vararg mediaTypes: DownloadMediaType,
    ) = DownloadUiModel(
        profileId = "main",
        videoId = videoId,
        mediaType = mediaTypes.first(),
        status = DownloadStatus.Completed,
        completedMediaTypes = mediaTypes.toSet(),
    )
}
