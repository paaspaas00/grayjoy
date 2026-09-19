package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveDownloadNavigationTest {
    @Test
    fun currentlyTransferringItemWinsOverQueuedItems() {
        val downloads = listOf(
            download("queued", DownloadStatus.Queued),
            download("paused", DownloadStatus.Paused),
            download("running", DownloadStatus.Downloading),
        )

        assertEquals("running", activeDownloadNavigationTarget(downloads))
    }

    @Test
    fun completedItemsAreNotNavigationTargets() {
        assertNull(
            activeDownloadNavigationTarget(
                listOf(download("complete", DownloadStatus.Completed)),
            ),
        )
    }

    private fun download(id: String, status: DownloadStatus) = DownloadUiModel(
        profileId = "main",
        videoId = id,
        status = status,
    )
}
