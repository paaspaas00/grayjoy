package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.engine.PlaybackStallGuard
import com.futo.platformplayer.compose.images.artworkMemoryBudget
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class PlaybackAndMemoryRegressionTest {
    @Test fun onlyAStalledBufferTriggersRecoveryAndSeeksResetTheTimer() {
        val guard = PlaybackStallGuard(12_000)
        assertFalse(guard.stalled("a", 1000, 2000, 0))
        assertFalse(guard.stalled("a", 1000, 3000, 11000))
        assertFalse(guard.stalled("a", 1000, 3000, 22999))
        assertTrue(guard.stalled("a", 1000, 3000, 23000))
        assertFalse(guard.stalled("a", 9000, 3000, 23001))
        assertFalse(guard.stalled("b", 9000, 3000, 50000))
    }

    @Test fun remoteCatalogRetainsMetadataWithoutKeepingEveryPlaybackClient() {
        val cache = RemoteVideoCache(3)
        repeat(1000) { i -> cache["$i"] = VideoUiModel("$i", "Title $i", "Channel", "", "1:00",
            playbackManifest = "large manifest $i", playbackUrl = "https://example.com/$i", isDownloaded = i == 0) }
        assertEquals(1000, cache.size)
        assertEquals(3, cache.values.count { it.playbackManifest.isNotBlank() })
        assertEquals("Title 0", cache["0"]?.title)
        assertTrue(cache.getValue("0").isDownloaded)
        cache.clear()
        assertTrue(cache.isEmpty())
    }

    @Test fun metadataHydrationDoesNotReplaceAnAlreadyVisibleAvatar() {
        val video = VideoUiModel("a", "Title", "Channel", "", "", authorThumbnailUrl = "existing")
        val result = video.withMetadataPresentation(MetadataPresentationPatch("4:00", "other-size", true))
        assertEquals("existing", result.authorThumbnailUrl)
        assertEquals("4:00", result.duration)
        assertEquals("missing", video.copy(authorThumbnailUrl = "").withMetadataPresentation(MetadataPresentationPatch(authorThumbnailUrl = "missing")).authorThumbnailUrl)
    }

    @Test fun imageBudgetLeavesMostOfSmallHeapsForPlaybackAndExtraction() {
        for (heapMiB in listOf(64L, 128L, 256L, 512L)) {
            val (images, pool) = artworkMemoryBudget(heapMiB * 1024 * 1024)
            assertTrue(images + pool <= heapMiB * 1024 * 1024 / 4)
            assertTrue(images <= 24L * 1024 * 1024)
            assertTrue(pool <= 8L * 1024 * 1024)
        }
    }
}
