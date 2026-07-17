package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSnapshotMergeTest {
    @Test
    fun videoCanDownloadWhileCompletedAudioRemainsAvailable() {
        val completedAudio = snapshot(
            mediaType = DownloadMediaType.Audio,
            status = DownloadStatus.Completed,
            completed = setOf(DownloadMediaType.Audio),
        )
        val downloadingVideo = snapshot(
            mediaType = DownloadMediaType.Video,
            status = DownloadStatus.Downloading,
            active = setOf(DownloadMediaType.Video),
        )

        val merged = mergeDownloadSnapshots(completedAudio, downloadingVideo)

        assertEquals(DownloadStatus.Downloading, merged.status)
        assertTrue(merged.isComplete(DownloadMediaType.Audio))
        assertTrue(merged.isActive(DownloadMediaType.Video))
    }

    @Test
    fun failedUpgradeDoesNotHideExistingOfflineMedia() {
        val completedAudio = snapshot(
            mediaType = DownloadMediaType.Audio,
            status = DownloadStatus.Completed,
            completed = setOf(DownloadMediaType.Audio),
        )
        val failedVideo = snapshot(
            mediaType = DownloadMediaType.Video,
            status = DownloadStatus.Failed,
            failed = setOf(DownloadMediaType.Video),
        )

        val merged = mergeDownloadSnapshots(completedAudio, failedVideo)

        assertEquals(DownloadStatus.Failed, merged.status)
        assertTrue(merged.isComplete)
        assertTrue(merged.isComplete(DownloadMediaType.Audio))
        assertTrue(DownloadMediaType.Video in merged.failedMediaTypes)
    }

    private fun snapshot(
        mediaType: DownloadMediaType,
        status: DownloadStatus,
        completed: Set<DownloadMediaType> = emptySet(),
        active: Set<DownloadMediaType> = emptySet(),
        failed: Set<DownloadMediaType> = emptySet(),
    ) = DownloadUiModel(
        profileId = "main",
        videoId = "video",
        mediaType = mediaType,
        status = status,
        completedMediaTypes = completed,
        activeMediaTypes = active,
        failedMediaTypes = failed,
    )
}
