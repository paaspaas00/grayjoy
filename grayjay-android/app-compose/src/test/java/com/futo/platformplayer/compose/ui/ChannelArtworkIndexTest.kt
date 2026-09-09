package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class ChannelArtworkIndexTest {
    private fun channel(id: String, source: String, image: String) = ChannelUiModel(id, "Creator", source, "", 0, "", "", image)
    @Test fun blankAvatarCannotHideKnownArtworkAndSourcesStayIsolated() {
        val index = ChannelArtworkIndex(listOf(channel("https://example/channel", "one", ""), channel("https://example/channel", "one", "image"), channel("https://other/channel", "two", "other")))
        assertEquals("image", index.find(VideoUiModel("video", "Title", "Creator", "", "", sourceId = "one", authorUrl = "https://example/channel/")))
        assertNull(index.find(VideoUiModel("video", "Title", "Creator", "", "", sourceId = "unknown")))
    }
    @Test fun ambiguousCreatorNamesAreNotUsedAsFallback() {
        val index = ChannelArtworkIndex(listOf(channel("https://example/a", "one", "a"), channel("https://example/b", "one", "b")))
        assertNull(index.find(VideoUiModel("v", "Title", "Creator", "", "", sourceId = "one")))
    }
    @Test fun channelIdAliasesReuseTheFreshestKnownAvatar() {
        val index = ChannelArtworkIndex(listOf(channel("https://www.example/channel/AbC", "one", "fresh"), channel("https://example/channel/AbC/", "one", "old")))
        assertEquals("fresh", index.find(VideoUiModel("v", "Title", "Other label", "", "", sourceId = "one", authorUrl = "https://m.example/channel/AbC")))
    }
    @Test fun youtubeRawChannelIdMatchesItsCanonicalUrl() {
        val id = "UCabcdefghijklmnopqrstuv"
        val index = ChannelArtworkIndex(listOf(channel("https://www.youtube.com/channel/$id", "youtube", "avatar")))
        assertEquals("avatar", index.find(VideoUiModel("v", "Title", "Creator", "", "", sourceId = "youtube", channelId = id)))
    }
}
