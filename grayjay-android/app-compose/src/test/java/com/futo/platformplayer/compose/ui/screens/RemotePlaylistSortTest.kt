package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePlaylistSortTest {
    @Test
    fun popularityAndUploadDateRespectDirectionAndKeepUnknownLast() {
        val videos = listOf(
            video("older-popular", views = 500, uploaded = 10),
            video("newer-small", views = 20, uploaded = 30),
            video("unknown", views = 0, uploaded = 0),
        )

        assertEquals(
            listOf("older-popular", "newer-small", "unknown"),
            sortedRemotePlaylistVideos(videos, RemotePlaylistSortMode.Popularity, false)
                .map(VideoUiModel::id),
        )
        assertEquals(
            listOf("newer-small", "older-popular", "unknown"),
            sortedRemotePlaylistVideos(videos, RemotePlaylistSortMode.UploadDate, false)
                .map(VideoUiModel::id),
        )
    }

    private fun video(id: String, views: Long, uploaded: Long) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        sourceId = "youtube",
        viewCount = views,
        publishedAtMs = uploaded,
    )
}
