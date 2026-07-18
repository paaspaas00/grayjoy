package com.futo.platformplayer.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPlaybackTest {
    @Test
    fun fromHereStartsAtSelectedVideoAndKeepsFollowingOrder() {
        assertEquals(
            listOf("third", "fourth"),
            playlistQueueFrom(
                videoIds = listOf("first", "second", "third", "fourth"),
                selectedVideoId = "third",
            ),
        )
    }

    @Test
    fun fromHereRejectsVideoOutsidePlaylist() {
        assertTrue(
            playlistQueueFrom(
                videoIds = listOf("first", "second"),
                selectedVideoId = "missing",
            ).isEmpty(),
        )
    }
}
