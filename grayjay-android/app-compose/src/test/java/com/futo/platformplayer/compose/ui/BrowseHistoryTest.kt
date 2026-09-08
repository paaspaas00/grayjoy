package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class BrowseHistoryTest {
    @Test fun `nested routes retain their actual playlist and player origins`() {
        val playlist = BrowseRoute("Library", playlistId = "local", libraryFilter = "Playlists")
        val player = playlist.copy(videoId = "playing")
        val channel = BrowseRoute("Library", channelId = "creator")
        val stack = appendBrowseRoute(appendBrowseRoute(emptyList(), player), channel)
        assertEquals(channel, stack.last())
        assertEquals(player, stack.dropLast(1).last())
    }
    @Test fun `repeated clicks do not add duplicate origins and history is bounded`() {
        val route = BrowseRoute("Search", channelId = "creator")
        assertEquals(listOf(route), appendBrowseRoute(listOf(route), route))
        val stack = (0..60).fold(emptyList<BrowseRoute>()) { history, index ->
            appendBrowseRoute(history, route.copy(channelId = "$index"))
        }
        assertEquals(40, stack.size)
        assertEquals("60", stack.last().channelId)
    }
}
