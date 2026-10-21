package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.junit.Assert.*
import org.junit.Test

class OfflinePlaylistSyncPolicyTest {
    private val descriptor = OfflinePlaylistDownload("main", "playlist", DownloadMediaType.Audio, setOf("video"), targetVideoHeight = 720)
    private fun candidate(records: List<OfflinePlaylistDownload>, download: DownloadUiModel? = null) =
        automaticPlaylistDownloadCandidate(records, "main", "video", DownloadMediaType.Audio, download)

    @Test fun lateCancellationAndRemovedSubscriptionsPreventQueuedAutomaticStarts() {
        assertEquals(descriptor, candidate(listOf(descriptor)))
        assertNull(candidate(listOf(descriptor.copy(excludedVideoIds = setOf("video")))))
        assertNull(candidate(emptyList()))
        assertNull(candidate(listOf(descriptor.copy(managedVideoIds = emptySet()))))
        assertNull(candidate(listOf(descriptor.copy(profileId = "other"))))
        assertNull(candidate(listOf(descriptor.copy(mediaType = DownloadMediaType.Video))))
    }

    @Test fun currentUserPauseAndRemovalStayPausedWhileFailedTransfersCanRetry() {
        for (status in DownloadStatus.entries) {
            val download = DownloadUiModel("main", "video", DownloadMediaType.Audio, status)
            assertEquals(status.name, status == DownloadStatus.Failed, candidate(listOf(descriptor), download) != null)
        }
    }

    @Test fun latestTargetSettingsAreUsedWithoutDeletingSharedOrExcludedMedia() {
        val latest = descriptor.copy(targetVideoHeight = 1080)
        assertEquals(1080, candidate(listOf(latest))?.targetVideoHeight)
        assertTrue(offlinePlaylistHasOwner(listOf(descriptor.copy(excludedVideoIds = setOf("video"))),
            "main", "video", DownloadMediaType.Audio))
        assertFalse(offlinePlaylistHasOwner(listOf(descriptor), "main", "video", DownloadMediaType.Video))
    }
}
