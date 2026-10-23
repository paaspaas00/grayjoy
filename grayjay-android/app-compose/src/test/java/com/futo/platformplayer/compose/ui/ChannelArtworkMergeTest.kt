package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class ChannelArtworkMergeTest {
    private fun channel(id: String, image: String, source: String = "youtube") =
        ChannelUiModel(id, "Creator", source, "", 7, "Followers", "Description", image)

    @Test fun refreshedAvatarSurvivesLibrarySnapshotAndRetainsLibraryMetadata() {
        val saved = channel("https://www.youtube.com/channel/UC123", "")
        val fresh = saved.copy(thumbnailUrl = "fresh", unreadCount = 0, description = "")
        assertEquals(saved.copy(thumbnailUrl = "fresh"), mergeKnownChannelArtwork(listOf(saved), listOf(fresh)).single())
    }
    @Test fun rawIdsAndCanonicalUrlsMergeButSourcesStayIsolated() {
        val saved = channel("UC123", "old")
        val fresh = channel("https://www.youtube.com/channel/UC123/", "fresh")
        val other = channel("UC123", "another", "other")
        val result = mergeKnownChannelArtwork(listOf(saved, other), listOf(fresh))
        assertEquals(2, result.size)
        assertEquals(listOf("fresh", "another"), result.map { it.thumbnailUrl })
    }
    @Test fun failedRefreshCannotEraseSavedImageAndNewChannelsAreIncluded() {
        val saved = channel("UC123", "old")
        val fresh = channel("UC123", "")
        assertEquals(listOf("old", "new"), mergeKnownChannelArtwork(listOf(saved),
            listOf(fresh, channel("UC456", "new"))).map { it.thumbnailUrl })
    }
}
