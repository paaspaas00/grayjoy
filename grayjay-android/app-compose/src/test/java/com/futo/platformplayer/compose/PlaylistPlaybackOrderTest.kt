package com.futo.platformplayer.compose

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlaylistPlaybackOrderTest {
    @Test fun shuffleKeepsSelectedVideoFirstAndContainsEveryVideoOnce() {
        val ids = (1..30).map(Int::toString)
        repeat(200) { seed ->
            val queue = playlistPlaybackOrder(ids + ids, "17", true, Random(seed))
            assertEquals("17", queue.first())
            assertEquals(ids.toSet(), queue.toSet())
            assertEquals(ids.size, queue.size)
        }
    }
    @Test fun sequentialPlayFromHereRetainsTheOriginalForwardOrder() {
        assertEquals(listOf("b", "c"), playlistPlaybackOrder(listOf("a", "b", "c"), "b", false))
        assertTrue(playlistPlaybackOrder(listOf("a"), "missing", true).isEmpty())
    }
}
