package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayjaySearchTest {
    private val corpus = SearchCorpus(
        videos = listOf(
            video("privacy-first-library", "Privacy first library", "youtube"),
            video("open-web", "Protect the open web", "odysee"),
        ),
        channels = listOf(
            ChannelUiModel(
                id = "privacy-channel",
                name = "Privacy channel",
                sourceId = "youtube",
                source = "YouTube",
                unreadCount = 0,
                followerCount = "1 follower",
                description = "Privacy engineering",
            ),
        ),
        playlists = listOf(
            PlaylistUiModel(
                id = "privacy-and-freedom",
                title = "Privacy and freedom",
                description = "Private media",
                videoIds = listOf("privacy-first-library"),
            ),
        ),
    )

    @Test
    fun ranksVideoMatchesAcrossEnabledSources() {
        val result = searchContent(
            query = "privacy",
            enabledSourceIds = setOf("youtube", "odysee", "peertube"),
            corpus = corpus,
        )

        assertEquals("privacy-first-library", result.videos.first().id)
        assertEquals("privacy-and-freedom", result.playlists.first().id)
    }

    @Test
    fun excludesResultsFromDisabledSources() {
        val result = searchContent(
            query = "open web",
            enabledSourceIds = setOf("youtube"),
            corpus = corpus,
        )

        assertTrue(result.videos.isEmpty())
    }

    private fun video(id: String, title: String, sourceId: String) = VideoUiModel(
        id = id,
        title = title,
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        sourceId = sourceId,
    )
}
