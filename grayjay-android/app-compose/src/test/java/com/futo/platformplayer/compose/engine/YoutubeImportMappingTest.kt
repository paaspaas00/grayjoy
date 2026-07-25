package com.futo.platformplayer.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeImportMappingTest {
    @Test
    fun `history playback time becomes normalized watch progress`() {
        assertEquals(0.25f, importedWatchProgress(30, 120), 0.0001f)
        assertEquals(1f, importedWatchProgress(200, 120), 0.0001f)
        assertEquals(0f, importedWatchProgress(-1, 120), 0.0001f)
        assertEquals(0f, importedWatchProgress(30, 0), 0.0001f)
    }

    @Test
    fun `liked playlist recognizes youtube list parameter regardless of host shape`() {
        assertTrue(isYoutubeLikedPlaylistUrl("https://www.youtube.com/playlist?list=LL"))
        assertTrue(isYoutubeLikedPlaylistUrl("https://m.youtube.com/playlist?foo=1&list=LL"))
        assertFalse(isYoutubeLikedPlaylistUrl("https://www.youtube.com/playlist?list=WL"))
        assertFalse(isYoutubeLikedPlaylistUrl("https://example.com/playlist/LL"))
    }

    @Test
    fun `history timestamps preserve server order when dates are missing or coarse`() {
        assertEquals(
            listOf(9_000L, 8_999L, 8_000L, 7_999L, 7_998L),
            orderedImportedHistoryTimestamps(
                remoteTimestamps = listOf(9_000L, null, 8_000L, null, 8_500L),
                fallbackNow = 10_000L,
            ),
        )
    }

    @Test
    fun `history timestamps use import time only for undated leading rows`() {
        assertEquals(
            listOf(10_000L, 9_999L, 7_000L),
            orderedImportedHistoryTimestamps(
                remoteTimestamps = listOf(null, null, 7_000L),
                fallbackNow = 10_000L,
            ),
        )
    }
}
