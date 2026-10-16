package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.engine.PlaybackStallGuard
import com.futo.platformplayer.compose.engine.selectSpotifyYoutubeCandidate
import com.futo.platformplayer.compose.engine.spotifyYoutubeSearchQuery
import com.futo.platformplayer.compose.images.artworkMemoryBudget
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.screens.playlistDisplayVideoCount
import com.futo.platformplayer.compose.ui.screens.reconcileSearchSourceSelection
import org.junit.Assert.*
import org.junit.Test

class PlaybackAndMemoryRegressionTest {
    @Test fun remotePlaylistDoesNotClaimZeroVideosWhenItsCountIsUnknown() {
        val remote = PlaylistUiModel("remote", "Remote", "", emptyList(), sourceId = "spotify", videoCount = -1)
        val local = PlaylistUiModel("local", "Local", "", listOf("a", "b"))
        assertNull(playlistDisplayVideoCount(remote))
        assertEquals(2, playlistDisplayVideoCount(local))
        assertEquals(0, playlistDisplayVideoCount(remote.copy(videoCount = 0)))
    }

    @Test fun searchAutomaticallySelectsSourcesEnabledAfterOpeningOrChangingProfile() {
        assertEquals(
            listOf("spotify"),
            reconcileSearchSourceSelection(emptyList(), emptyList(), listOf("spotify"), initialized = true),
        )
        assertEquals(
            listOf("youtube", "spotify"),
            reconcileSearchSourceSelection(
                selectedIds = listOf("youtube"),
                previousActiveIds = listOf("youtube"),
                activeIds = listOf("youtube", "spotify"),
                initialized = true,
            ),
        )
        assertEquals(
            listOf("spotify"),
            reconcileSearchSourceSelection(
                selectedIds = listOf("spotify"),
                previousActiveIds = listOf("youtube", "spotify"),
                activeIds = listOf("spotify"),
                initialized = true,
            ),
        )
    }

    @Test fun spotifyBridgePrefersTheMatchingOfficialTrackAndRejectsUnrelatedResults() {
        val spotify = VideoUiModel(
            id = "https://open.spotify.com/track/example",
            title = "No Surprises",
            creator = "Radiohead",
            metadata = "",
            duration = "3:49",
            sourceId = "spotify",
            contentUrl = "https://open.spotify.com/track/example",
        )
        val cover = spotify.copy(id = "cover", title = "No Surprises cover", creator = "Bedroom singer", duration = "3:49", sourceId = "youtube")
        val unrelated = spotify.copy(id = "wrong", title = "Radiohead interview", creator = "News", duration = "3:49", sourceId = "youtube")
        val official = spotify.copy(id = "official", title = "No Surprises", creator = "Radiohead - Topic", duration = "3:49", sourceId = "youtube")

        assertEquals("Radiohead No Surprises official audio", spotifyYoutubeSearchQuery(spotify))
        assertEquals("official", selectSpotifyYoutubeCandidate(spotify, listOf(cover, unrelated, official))?.id)
        assertNull(selectSpotifyYoutubeCandidate(spotify, listOf(unrelated.copy(title = "Completely different song", creator = "Other"))))
    }

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
