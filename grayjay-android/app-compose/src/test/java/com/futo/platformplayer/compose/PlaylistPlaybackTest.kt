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

    @Test
    fun preparedItemsAheadCountsOnlyEntriesAfterCurrentVideo() {
        assertEquals(
            2,
            preparedQueueItemsAhead(
                queueVideoIds = listOf("first", "second", "third", "fourth"),
                currentVideoId = "second",
            ),
        )
    }

    @Test
    fun preparedItemsAheadIsZeroBeforeMedia3HasCurrentVideo() {
        assertEquals(
            0,
            preparedQueueItemsAhead(
                queueVideoIds = listOf("first", "second"),
                currentVideoId = null,
            ),
        )
        assertEquals(
            0,
            preparedQueueItemsAhead(
                queueVideoIds = listOf("first", "second"),
                currentVideoId = "missing",
            ),
        )
    }
}
