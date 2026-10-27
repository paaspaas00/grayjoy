package com.futo.platformplayer.compose.jobs

import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.*
import com.futo.platformplayer.compose.downloads.DownloadBatchProgress
import org.junit.Assert.*
import org.junit.Test

class JobCompletionTrackerTest {
    private fun download(id: String, status: DownloadStatus) = DownloadUiModel("main", id, status = status)
    @Test fun multipleDownloadsProduceOnlyOneNoticeAfterTheWholeBatch() {
        val tracker = JobCompletionTracker()
        val start = GrayjayUiState(downloads = mapOf("a" to download("a", DownloadStatus.Queued), "b" to download("b", DownloadStatus.Downloading)),
            downloadBatchProgress = DownloadBatchProgress(2))
        assertTrue(tracker.observe(start).isEmpty())
        val half = start.copy(downloads = start.downloads + ("a" to download("a", DownloadStatus.Completed)), downloadCompletionGeneration = 1)
        assertTrue(tracker.observe(half).isEmpty())
        val end = half.copy(downloads = half.downloads + ("b" to download("b", DownloadStatus.Completed)), downloadBatchProgress = DownloadBatchProgress(2, 2, 1f), downloadCompletionGeneration = 2)
        assertEquals(listOf(R.string.downloads), tracker.observe(end)); assertTrue(tracker.observe(end).isEmpty())
    }
    @Test fun pauseAndProfileChangesAreNotCompletion() {
        val tracker = JobCompletionTracker()
        val start = GrayjayUiState(downloads = mapOf("a" to download("a", DownloadStatus.Downloading)), downloadBatchProgress = DownloadBatchProgress(1))
        tracker.observe(start)
        assertTrue(tracker.observe(start.copy(downloads = mapOf("a" to download("a", DownloadStatus.Paused)))).isEmpty())
        assertTrue(tracker.observe(start.copy(activeProfileId = "other", downloads = emptyMap())).isEmpty())
    }
    @Test fun queuedPreparationKeepsTheBatchOpen() {
        val tracker = JobCompletionTracker()
        val state = GrayjayUiState(activePlaylistDownloads = setOf(PlaylistDownloadBatchUiModel("p", DownloadMediaType.Audio)),
            downloadBatchProgress = DownloadBatchProgress(3))
        tracker.observe(state)
        val completed = state.copy(downloads = mapOf("a" to download("a", DownloadStatus.Completed)),
            downloadCompletionGeneration = 3, downloadBatchProgress = DownloadBatchProgress(3, 3, 1f))
        assertTrue(tracker.observe(completed).isEmpty())
        assertEquals(listOf(R.string.downloads), tracker.observe(completed.copy(activePlaylistDownloads = emptySet())))
    }

    @Test fun restoringOldPlaylistDescriptorsAndCompletedDownloadsIsSilent() {
        val tracker = JobCompletionTracker()
        tracker.observe(GrayjayUiState())
        val restoring = GrayjayUiState(activePlaylistDownloads = setOf(PlaylistDownloadBatchUiModel("old", DownloadMediaType.Audio)))
        assertTrue(tracker.observe(restoring).isEmpty())
        val restored = restoring.copy(activePlaylistDownloads = emptySet(),
            downloads = mapOf("a" to download("a", DownloadStatus.Completed)),
            downloadBatchProgress = DownloadBatchProgress(100, 100, 1f))
        repeat(10) { assertTrue(tracker.observe(restored).isEmpty()) }
    }

    @Test fun failureOrCancellationAfterPartialSuccessIsNotAllCompleted() {
        for (status in listOf(DownloadStatus.Failed, DownloadStatus.Paused)) {
            val tracker = JobCompletionTracker()
            val start = GrayjayUiState(downloads = mapOf("a" to download("a", DownloadStatus.Downloading),
                "b" to download("b", DownloadStatus.Downloading)), downloadBatchProgress = DownloadBatchProgress(2))
            tracker.observe(start)
            val partial = start.copy(downloads = start.downloads + ("a" to download("a", DownloadStatus.Completed)),
                downloadCompletionGeneration = 1, downloadBatchProgress = DownloadBatchProgress(2, 1, .5f))
            assertTrue(tracker.observe(partial).isEmpty())
            assertTrue(tracker.observe(partial.copy(downloads = partial.downloads + ("b" to download("b", status)))).isEmpty())
            assertTrue(tracker.observe(partial.copy(downloads = emptyMap(), downloadBatchProgress = DownloadBatchProgress())).isEmpty())
        }
    }

    @Test fun switchingProfileNeverReplaysItsOldCompletionGeneration() {
        val tracker = JobCompletionTracker()
        tracker.observe(GrayjayUiState(downloadCompletionGeneration = 9))
        tracker.observe(GrayjayUiState(activeProfileId = "other", downloadCompletionGeneration = 20))
        assertTrue(tracker.observe(GrayjayUiState(downloadCompletionGeneration = 9,
            downloadBatchProgress = DownloadBatchProgress(3, 3, 1f))).isEmpty())
    }

    @Test fun cancellingTheRemainderDoesNotAnnounceTheAlreadyCompletedFiles() {
        val tracker = JobCompletionTracker()
        val start = GrayjayUiState(downloads = mapOf("a" to download("a", DownloadStatus.Downloading),
            "b" to download("b", DownloadStatus.Downloading)), downloadBatchProgress = DownloadBatchProgress(2))
        tracker.observe(start)
        val partial = start.copy(downloads = start.downloads + ("a" to download("a", DownloadStatus.Completed)),
            downloadCompletionGeneration = 1, downloadBatchProgress = DownloadBatchProgress(2, 1, .5f))
        tracker.observe(partial)
        assertTrue(tracker.observe(partial.copy(downloads = partial.downloads - "b",
            downloadBatchProgress = DownloadBatchProgress(1, 1, 1f))).isEmpty())
    }
}
