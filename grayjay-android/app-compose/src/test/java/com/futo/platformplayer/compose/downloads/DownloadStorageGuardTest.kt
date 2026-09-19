package com.futo.platformplayer.compose.downloads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStorageGuardTest {
    @Test
    fun healthyStorageDoesNotWarnOrPause() {
        val status = assessDownloadStorage(
            availableBytes = 2.gib,
            totalBytes = 10.gib,
            platformLowBytes = 200.mib,
            knownRemainingBytes = 0L,
            wasPaused = false,
        )

        assertFalse(status.isWarning)
        assertFalse(status.downloadsPaused)
    }

    @Test
    fun warningAppearsBeforeTheHardPauseThreshold() {
        val status = assessDownloadStorage(
            availableBytes = 700.mib,
            totalBytes = 10.gib,
            platformLowBytes = 200.mib,
            knownRemainingBytes = 0L,
            wasPaused = false,
        )

        assertTrue(status.isWarning)
        assertFalse(status.downloadsPaused)
    }

    @Test
    fun pausesBeforeReserveWouldBeConsumed() {
        val status = assessDownloadStorage(
            availableBytes = 200.mib,
            totalBytes = 10.gib,
            platformLowBytes = 200.mib,
            knownRemainingBytes = 0L,
            wasPaused = false,
        )

        assertTrue(status.isWarning)
        assertTrue(status.downloadsPaused)
    }

    @Test
    fun knownRemainingDownloadCannotConsumeTheReserve() {
        val status = assessDownloadStorage(
            availableBytes = 1100.mib,
            totalBytes = 10.gib,
            platformLowBytes = 200.mib,
            knownRemainingBytes = 1.gib,
            wasPaused = false,
        )

        assertTrue(status.downloadsPaused)
        assertTrue(status.requiredFreeBytes > status.availableBytes)
    }

    @Test
    fun pausedDownloadUsesHysteresisBeforeResuming() {
        val stillPaused = assessDownloadStorage(
            availableBytes = 1300.mib,
            totalBytes = 10.gib,
            platformLowBytes = 200.mib,
            knownRemainingBytes = 1.gib,
            wasPaused = true,
        )
        val resumed = assessDownloadStorage(
            availableBytes = 1500.mib,
            totalBytes = 10.gib,
            platformLowBytes = 200.mib,
            knownRemainingBytes = 1.gib,
            wasPaused = true,
        )

        assertTrue(stillPaused.downloadsPaused)
        assertFalse(resumed.downloadsPaused)
    }

    private val Int.mib: Long get() = toLong() * 1024L * 1024L
    private val Int.gib: Long get() = toLong() * 1024L * 1024L * 1024L
}
