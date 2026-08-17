package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSortTest {
    @Test
    fun `channel search stacks before the sort chip can squeeze it`() {
        assertTrue(channelSearchUsesStackedLayout(393f))
        assertFalse(channelSearchUsesStackedLayout(600f))
    }

    @Test
    fun `video sorting respects criterion and keeps unknown metadata last`() {
        val videos = listOf(
            video("older-popular", views = 900, uploaded = 10),
            video("newer-small", views = 30, uploaded = 30),
            video("unknown", views = 0, uploaded = 0),
        )

        assertEquals(
            listOf("newer-small", "older-popular", "unknown"),
            sortChannelVideos(videos, ChannelSortMode.UploadDate, ascending = false)
                .map(VideoUiModel::id),
        )
        assertEquals(
            listOf("newer-small", "older-popular", "unknown"),
            sortChannelVideos(videos, ChannelSortMode.Popularity, ascending = true)
                .map(VideoUiModel::id),
        )
    }

    @Test
    fun `playlist title sorting supports both directions`() {
        val playlists = listOf(playlist("Zulu"), playlist("Alpha"))
        assertEquals(
            listOf("Alpha", "Zulu"),
            sortChannelPlaylists(playlists, ascending = true).map(PlaylistUiModel::title),
        )
        assertEquals(
            listOf("Zulu", "Alpha"),
            sortChannelPlaylists(playlists, ascending = false).map(PlaylistUiModel::title),
        )
    }

    private fun video(id: String, views: Long, uploaded: Long) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        viewCount = views,
        publishedAtMs = uploaded,
    )

    private fun playlist(title: String) = PlaylistUiModel(
        id = title,
        title = title,
        description = "",
        videoIds = emptyList(),
    )
}
