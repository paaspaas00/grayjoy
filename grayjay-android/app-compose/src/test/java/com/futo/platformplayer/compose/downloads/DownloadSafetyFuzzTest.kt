package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.cancellableDownloadPairs
import com.futo.platformplayer.compose.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import kotlin.random.Random

class DownloadSafetyFuzzTest {
    @Test fun storageArithmeticNeverWrapsOrResumesIntoTheReserve() {
        val random = Random(19092026)
        repeat(20_000) {
            val available = random.nextLong(0, Long.MAX_VALUE)
            val total = random.nextLong(1, Long.MAX_VALUE)
            val remaining = random.nextLong(0, Long.MAX_VALUE)
            val status = assessDownloadStorage(available, total, 0, remaining, random.nextBoolean())
            val required = BigInteger.valueOf(status.reserveBytes) + BigInteger.valueOf(remaining)
            val expected = required.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
            assertEquals(expected, status.requiredFreeBytes)
            if (!status.downloadsPaused) {
                assertTrue(available > status.reserveBytes)
                assertTrue(available >= expected)
            }
        }
    }

    @Test fun staleQueueRowsCanNeverCancelCompletedMedia() {
        val random = Random(1984)
        repeat(5_000) {
            val type = DownloadMediaType.entries.random(random)
            val status = DownloadStatus.entries.random(random)
            val queued = QueuedDownload("main", "video", type, status, 1)
            val completed = DownloadUiModel("main", "video", type, DownloadStatus.Completed)
            val stale = completed.copy(status = DownloadStatus.Downloading, completedMediaTypes = emptySet())
            assertFalse(cancellableDownloadPairs(listOf(queued), listOf(stale, completed))
                .contains("video" to type))
        }
    }

    @Test fun managerPauseWinsOverQueuedAndRunningButNotCompletedOrFailed() {
        assertEquals(DownloadStatus.Paused, aggregateDownloadStatus(
            false, false, false, false, false, true, false, managerPaused = true))
        assertEquals(DownloadStatus.Paused, aggregateDownloadStatus(
            false, false, false, false, false, false, false, managerPaused = true))
        assertEquals(DownloadStatus.Completed, aggregateDownloadStatus(
            false, false, false, true, true, false, false, managerPaused = true))
        assertFalse(canResumeManagedDownloads(false, true))
        assertFalse(canResumeManagedDownloads(true, false))
        assertTrue(canResumeManagedDownloads(false, false))
    }
}
