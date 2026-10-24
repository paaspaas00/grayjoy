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
        val half = start.copy(downloads = start.downloads + ("a" to download("a", DownloadStatus.Completed)))
        assertTrue(tracker.observe(half).isEmpty())
        val end = half.copy(downloads = half.downloads + ("b" to download("b", DownloadStatus.Completed)), downloadBatchProgress = DownloadBatchProgress(2, 2, 1f))
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
        assertTrue(tracker.observe(state.copy(downloads = mapOf("a" to download("a", DownloadStatus.Completed)))).isEmpty())
        assertEquals(listOf(R.string.downloads), tracker.observe(state.copy(activePlaylistDownloads = emptySet())))
    }
}
