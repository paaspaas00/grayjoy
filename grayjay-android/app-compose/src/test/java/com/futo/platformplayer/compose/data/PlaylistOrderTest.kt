package com.futo.platformplayer.compose.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderTest {
    @Test
    fun requestedOrderMovesExistingVideosAndPreservesAnyOmittedVideos() {
        assertEquals(
            listOf("third", "first", "second"),
            normalizePlaylistOrder(
                existingVideoIds = listOf("first", "second", "third"),
                requestedOrder = listOf("third", "first"),
            ),
        )
    }

    @Test
    fun requestedOrderRejectsUnknownAndDuplicateIds() {
        assertEquals(
            listOf("second", "first", "third"),
            normalizePlaylistOrder(
                existingVideoIds = listOf("first", "second", "third"),
                requestedOrder = listOf("unknown", "second", "second"),
            ),
        )
    }
}
