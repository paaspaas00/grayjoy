package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadSupportTest {
    @Test
    fun crunchyrollIsNeverOfferedAsOfflineDownloadBeforeOrAfterResolution() {
        assertFalse(video(sourceId = "crunchyroll").supportsOfflineDownload())
        assertFalse(video(sourceId = "crunchyroll", drm = true).supportsOfflineDownload())
    }

    @Test
    fun drmAndLiveContentAreRejectedButOrdinaryVodIsAllowed() {
        assertFalse(video(sourceId = "nebula", drm = true).supportsOfflineDownload())
        assertFalse(video(sourceId = "youtube", live = true).supportsOfflineDownload())
        assertTrue(video(sourceId = "youtube").supportsOfflineDownload())
    }

    private fun video(
        sourceId: String,
        drm: Boolean = false,
        live: Boolean = false,
    ) = VideoUiModel(
        id = "id",
        title = "title",
        creator = "creator",
        metadata = "",
        duration = "1:00",
        sourceId = sourceId,
        isDrmProtected = drm,
        isLive = live,
    )
}
