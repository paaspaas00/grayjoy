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

    @Test fun `job shortcuts preserve exact browse context without duplicating current page`() {
        val search = BrowseRoute("Search")
        val remotePlaylist = search.copy(playlistId = "remote")
        val downloads = BrowseRoute("Library", libraryFilter = "Downloads")
        val history = browseHistoryForShortcut(listOf(search), remotePlaylist, downloads)
        assertEquals(listOf(search, remotePlaylist), history)
        assertEquals(history, browseHistoryForShortcut(history, downloads, downloads))
    }

    @Test fun `saved routes retain nested source origin and migrate legacy history`() {
        val routes = listOf(BrowseRoute("Sources", parentDestination = "Settings"),
            BrowseRoute("Library", playlistId = "playlist", libraryFilter = "Playlists", videoId = "video"))
        assertEquals(routes, decodeBrowseHistory(encodeBrowseHistory(routes)))
        assertEquals(listOf(BrowseRoute("Search", channelId = "creator")),
            decodeBrowseHistory(listOf("Search", "creator", "", "History", "")))
        assertTrue(decodeBrowseHistory(listOf("routes-v2", "incomplete")).isEmpty())
    }

    @Test fun `closing miniplayer keeps browsing while closing expanded player restores playlist`() {
        assertFalse(shouldRestorePlaylistWhenClosingPlayer(null, false))
        assertTrue(shouldRestorePlaylistWhenClosingPlayer("video", false))
        assertTrue(shouldRestorePlaylistWhenClosingPlayer("video", true))
        assertEquals(0f, initialPlayerTransitionProgress("restored-video"), 0f)
        assertEquals(1f, initialPlayerTransitionProgress(null), 0f)
    }

    @Test fun `aborted miniplayer expansion never navigates to playback playlist`() {
        assertFalse(shouldRestorePlaylistOnPlayerSettle(1f, 1f))
        assertFalse(shouldRestorePlaylistOnPlayerSettle(1f, 0f))
        assertFalse(shouldRestorePlaylistOnPlayerSettle(0f, 0f))
        assertTrue(shouldRestorePlaylistOnPlayerSettle(0f, 1f))
    }

    @Test fun `remote playlist return does not skip the channel being browsed or reopen player`() {
        val search = BrowseRoute("Search")
        val channel = search.copy(channelId = "different-channel", videoId = "playing")
        val history = historyForRemotePlaybackPlaylistReturn(listOf(search), channel, "playing-playlist")
        assertEquals(channel.copy(videoId = null), history.last())
        assertEquals(history, historyForRemotePlaybackPlaylistReturn(history,
            search.copy(playlistId = "playing-playlist", videoId = "playing"), "playing-playlist"))
    }

    @Test fun `generated navigation histories preserve every retained parent and stay bounded`() {
        val random = java.util.Random(0xBAC2026)
        repeat(1_000) {
            var history = emptyList<BrowseRoute>()
            repeat(random.nextInt(120)) { index ->
                history = appendBrowseRoute(history, BrowseRoute(
                    destination = listOf("Home", "Search", "Library", "Sources")[random.nextInt(4)],
                    channelId = if (random.nextBoolean()) "channel-$index" else null,
                    playlistId = if (random.nextBoolean()) "playlist-$index" else null,
                    videoId = if (random.nextBoolean()) "video-$index" else null,
                    parentDestination = if (random.nextBoolean()) "Settings" else null,
                ))
            }
            assertTrue(history.size <= 40)
            assertEquals(history, decodeBrowseHistory(encodeBrowseHistory(history)))
        }
    }
}
