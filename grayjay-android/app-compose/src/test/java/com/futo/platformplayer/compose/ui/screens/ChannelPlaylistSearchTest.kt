package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import org.junit.Assert.*
import org.junit.Test

class ChannelPlaylistSearchTest {
    private val playlists = listOf(
        PlaylistUiModel("a", "Hardware", "Circuits and electronics", emptyList()),
        PlaylistUiModel("b", "Travel in ŁÓDŹ", "Journeys", emptyList()))
    @Test fun titlesDescriptionsCaseAndWhitespaceAreSearchableWithoutReordering() {
        assertSame(playlists, filterChannelPlaylists(playlists, "   "))
        assertEquals(listOf(playlists[0]), filterChannelPlaylists(playlists, " CIRCUITS "))
        assertEquals(listOf(playlists[1]), filterChannelPlaylists(playlists, "łódź"))
        assertTrue(filterChannelPlaylists(playlists, "missing").isEmpty())
        assertEquals(listOf("a", "b"), filterChannelPlaylists(playlists, "a").map { it.id })
    }
    @Test fun playlistSearchKeepsFetchingTheListingInsteadOfTheVideoSearchEndpoint() {
        for (tab in ChannelContentTab.entries) {
            assertTrue(channelSearchUsesListingPager(tab, ""))
            assertEquals(tab.isPlaylistTab, channelSearchUsesListingPager(tab, "query"))
        }
    }
}
