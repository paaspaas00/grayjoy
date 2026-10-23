package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DownloadBatchProgressTest {
    private fun item(id: String, status: DownloadStatus = DownloadStatus.Queued, progress: Float? = null,
        type: DownloadMediaType = DownloadMediaType.Audio) =
        DownloadUiModel("profile", id, type, status, progress)
    @Test fun completedMembersStayInTheTotalAndUnrelatedOldDownloadsDoNotCount() {
        val tracker = DownloadBatchTracker()
        val four = (1..4).map { item("$it") }
        val old = item("old", DownloadStatus.Completed)
        assertEquals(4, tracker.update(four + old).total)
        val progress = tracker.update(four.mapIndexed { i, v -> if (i == 0) v.copy(status = DownloadStatus.Completed) else v } + old)
        assertEquals(4, progress.total)
        assertEquals(1, progress.completed)
        assertEquals(.25f, progress.fraction, .0001f)
    }
    @Test fun persistedMembershipKeepsCompletedCountAfterRestartAndPause() {
        val tracker = DownloadBatchTracker()
        val items = listOf(item("1"), item("2"), item("3"), item("4"))
        tracker.update(items)
        val recreated = DownloadBatchTracker(tracker.keys())
        val result = recreated.update(items.mapIndexed { i, v -> v.copy(status =
            if (i == 0) DownloadStatus.Completed else DownloadStatus.Paused) })
        assertEquals(.25f, result.fraction, .0001f)
    }
    @Test fun nextBatchDoesNotIncludePreviousCompletedBatch() {
        val tracker = DownloadBatchTracker()
        tracker.update(listOf(item("1"), item("2")))
        tracker.update(listOf(item("1", DownloadStatus.Completed), item("2", DownloadStatus.Completed)))
        val result = tracker.update(listOf(item("1", DownloadStatus.Completed), item("2", DownloadStatus.Completed), item("3")))
        assertEquals(1, result.total)
        assertEquals(0f, result.fraction, 0f)
    }
    @Test fun alreadyDownloadedVideoIsNotMistakenForNewAudioCompletion() {
        val tracker = DownloadBatchTracker()
        val result = tracker.update(listOf(item("1").copy(completedMediaTypes = setOf(DownloadMediaType.Video),
            activeMediaTypes = setOf(DownloadMediaType.Audio))))
        assertEquals(0, result.completed)
        assertEquals(0f, result.fraction, 0f)
    }
    @Test fun cancellationAndNonFiniteProgressCannotCorruptTheAggregate() {
        val tracker = DownloadBatchTracker()
        tracker.update(listOf(item("a"), item("b")))
        assertEquals(1, tracker.update(listOf(item("a", progress = Float.NaN))).total)
        val random = Random(100)
        repeat(1000) {
            val rows = (1..random.nextInt(1, 40)).map { id ->
                item("$id", DownloadStatus.entries[random.nextInt(DownloadStatus.entries.size)],
                    listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f, .5f)[random.nextInt(5)])
            }
            val result = tracker.update(rows)
            assertTrue(result.fraction.isFinite() && result.fraction in 0f..1f)
            assertTrue(result.completed in 0..result.total)
        }
    }
}
