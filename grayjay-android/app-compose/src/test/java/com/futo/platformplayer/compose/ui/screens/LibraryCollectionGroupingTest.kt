package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.*
import org.junit.Assert.*
import org.junit.Test

class LibraryCollectionGroupingTest {
    private val videos = (1..4).map { VideoUiModel("v$it", "Song $it", "Channel ${it % 2}", "", "", channelId = "c${it % 2}") }
    @Test fun channelGroupingNeverLosesOrDuplicatesVideosAndKeepsSourcesSeparate() {
        val input = videos + videos[0].copy(id = "other-source", sourceId = "other")
        val index = indexLibraryCollection(input, emptyList(), LibraryGrouping.Channel)
        val folders = index.entries(emptySet()).filterIsInstance<HistoryListEntry.Folder>()
        assertEquals(3, folders.size)
        assertEquals(input.map { it.id }.toSet(), folders.flatMap { it.videos }.map { it.id }.toSet())
        assertEquals(input.size, folders.sumOf { it.videos.size })
    }
    @Test fun sharedPlaylistMembershipUsesUniqueRowKeysAndUnassignedVideosStayVisible() {
        val playlists = listOf(PlaylistUiModel("p1", "One", "", listOf("v1", "v2", "v2")),
            PlaylistUiModel("p2", "Two", "", listOf("v2", "v3")))
        val index = indexLibraryCollection(videos, playlists, LibraryGrouping.Playlist)
        val closed = index.entries(emptySet())
        val folders = closed.filterIsInstance<HistoryListEntry.Folder>()
        assertEquals(2, folders.size)
        assertEquals("v4", (closed.last() as HistoryListEntry.Video).video.id)
        val open = index.entries(folders.map { it.key }.toSet())
        assertEquals(open.size, open.map { it.key }.distinct().size)
        assertEquals(2, open.filterIsInstance<HistoryListEntry.Video>().count { it.video.id == "v2" })
    }
    @Test fun filteringBeforeGroupingOnlyShowsMatchesAndIgnoresTemporaryQueues() {
        val queue = PlaylistUiModel("q", "Queue", "", videos.map { it.id }, isPlaybackQueue = true)
        val matched = videosMatchingLibraryQuery(videos, "Song 2")
        val entries = indexLibraryCollection(matched, listOf(queue), LibraryGrouping.Playlist).entries(emptySet())
        assertEquals(listOf("v2"), entries.filterIsInstance<HistoryListEntry.Video>().map { it.video.id })
    }
    @Test fun randomMembershipsAndFiltersAlwaysKeepAllInputIds() {
        val random = kotlin.random.Random(90)
        repeat(100) {
            val playlists = (0..9).map { i -> PlaylistUiModel("p$i", "P$i", "", videos.filter { random.nextBoolean() }.map { it.id }) }
            val visible = videos.filter { random.nextBoolean() }
            val index = indexLibraryCollection(visible, playlists, LibraryGrouping.Playlist)
            val closed = index.entries(emptySet())
            val expanded = index.entries(closed.filterIsInstance<HistoryListEntry.Folder>().map { it.key }.toSet())
            assertEquals(visible.map { it.id }.toSet(), expanded.filterIsInstance<HistoryListEntry.Video>().map { it.video.id }.toSet())
            assertEquals(expanded.size, expanded.map { it.key }.distinct().size)
        }
    }
}
