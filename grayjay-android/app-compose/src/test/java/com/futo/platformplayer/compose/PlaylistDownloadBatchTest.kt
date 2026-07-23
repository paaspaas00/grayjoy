package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.downloads.OfflinePlaylistDownload
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.PlaylistDownloadBatchUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDownloadBatchTest {
    @Test
    fun batchRemainsActiveUntilEveryManagedItemCompletes() {
        val descriptor = OfflinePlaylistDownload(
            profileId = "main",
            playlistId = "playlist",
            mediaType = DownloadMediaType.Video,
            managedVideoIds = setOf("complete", "pending"),
        )
        val downloads = mapOf(
            "complete" to completed("complete", DownloadMediaType.Video),
            "pending" to DownloadUiModel(
                profileId = "main",
                videoId = "pending",
                mediaType = DownloadMediaType.Video,
                status = DownloadStatus.Paused,
                activeMediaTypes = setOf(DownloadMediaType.Video),
            ),
        )

        assertEquals(
            setOf(PlaylistDownloadBatchUiModel("playlist", DownloadMediaType.Video)),
            activePlaylistDownloadBatches(listOf(descriptor), downloads),
        )
    }

    @Test
    fun completedAndExplicitlyExcludedItemsDoNotKeepBatchActive() {
        val descriptor = OfflinePlaylistDownload(
            profileId = "main",
            playlistId = "playlist",
            mediaType = DownloadMediaType.Audio,
            managedVideoIds = setOf("complete", "cancelled"),
            excludedVideoIds = setOf("cancelled"),
        )

        assertEquals(
            emptySet<PlaylistDownloadBatchUiModel>(),
            activePlaylistDownloadBatches(
                listOf(descriptor),
                mapOf("complete" to completed("complete", DownloadMediaType.Audio)),
            ),
        )
    }

    @Test
    fun cancellationKeepsCompletedAndSharedDownloads() {
        val cancelled = OfflinePlaylistDownload(
            profileId = "main",
            playlistId = "first",
            mediaType = DownloadMediaType.Video,
            managedVideoIds = setOf("complete", "shared", "partial"),
        )
        val remaining = OfflinePlaylistDownload(
            profileId = "main",
            playlistId = "second",
            mediaType = DownloadMediaType.Video,
            managedVideoIds = setOf("shared"),
        )

        assertEquals(
            setOf("partial"),
            pendingPlaylistCancellationIds(
                cancelled = cancelled,
                remainingDescriptors = listOf(remaining),
                downloads = mapOf(
                    "complete" to completed("complete", DownloadMediaType.Video),
                    "partial" to DownloadUiModel(
                        profileId = "main",
                        videoId = "partial",
                        mediaType = DownloadMediaType.Video,
                        status = DownloadStatus.Downloading,
                    ),
                ),
            ),
        )
    }

    private fun completed(
        videoId: String,
        mediaType: DownloadMediaType,
    ) = DownloadUiModel(
        profileId = "main",
        videoId = videoId,
        mediaType = mediaType,
        status = DownloadStatus.Completed,
        completedMediaTypes = setOf(mediaType),
    )
}
