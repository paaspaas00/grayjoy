package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private val videos = listOf(
        video(id = "watch-later", isWatchLater = true),
        video(id = "download", isDownloaded = true),
        video(id = "history", watchProgress = 0.4f),
        video(id = "playlist", playlistNames = listOf("Favorites")),
    )

    @Test
    fun eachFilterSelectsOnlyItsMatchingVideos() {
        assertEquals(listOf("watch-later"), idsFor(LibraryFilter.WatchLater))
        assertEquals(listOf("playlist"), idsFor(LibraryFilter.Playlists))
        assertEquals(listOf("download"), idsFor(LibraryFilter.Downloads))
        assertEquals(listOf("history"), idsFor(LibraryFilter.History))
    }

    @Test
    fun downloadsFilterIncludesInProgressMedia() {
        val downloads = mapOf(
            "history" to DownloadUiModel(
                profileId = "main",
                videoId = "history",
                status = DownloadStatus.Downloading,
                progress = 0.5f,
            ),
        )

        assertEquals(
            listOf("download", "history"),
            videosForLibraryFilter(videos, LibraryFilter.Downloads, downloads)
                .map(VideoUiModel::id),
        )
    }

    private fun idsFor(filter: LibraryFilter) =
        videosForLibraryFilter(videos, filter).map(VideoUiModel::id)

    private fun video(
        id: String,
        watchProgress: Float = 0f,
        isDownloaded: Boolean = false,
        isWatchLater: Boolean = false,
        playlistNames: List<String> = emptyList(),
    ) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "Now",
        duration = "1:00",
        watchProgress = watchProgress,
        isDownloaded = isDownloaded,
        isWatchLater = isWatchLater,
        playlistNames = playlistNames,
    )
}
