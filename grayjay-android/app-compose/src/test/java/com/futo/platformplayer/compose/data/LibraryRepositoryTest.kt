package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {
    @Test
    fun libraryStateOverridesOnlyMatchingContent() {
        val videos = listOf(video("one"), video("two", isDownloaded = true))
        val state = mapOf(
            "one" to LibraryVideoState(
                isWatchLater = true,
                isDownloaded = true,
                watchProgress = 0.75f,
            ),
        )

        val merged = videos.withLibraryState(state)

        assertTrue(merged[0].isWatchLater)
        assertTrue(merged[0].isDownloaded)
        assertEquals(0.75f, merged[0].watchProgress)
        assertFalse(merged[1].isWatchLater)
        assertTrue(merged[1].isDownloaded)
    }

    @Test
    fun playlistNamesAreComparedCaseInsensitivelyAndIgnoreExtraWhitespace() {
        assertTrue(playlistTitleExists("  Road   trip ", listOf("Road Trip")))
        assertFalse(playlistTitleExists("Road trips", listOf("Road Trip")))
    }

    @Test
    fun duplicateRemotePlaylistAddsChannelAndThenNumericSuffix() {
        assertEquals(
            "Favorites - Example Channel",
            uniqueRemotePlaylistTitle(
                requestedTitle = "Favorites",
                channelName = "Example Channel",
                existingTitles = listOf("Favorites"),
                fallbackTitle = "Imported playlist",
            ),
        )
        assertEquals(
            "Favorites - Example Channel (2)",
            uniqueRemotePlaylistTitle(
                requestedTitle = "Favorites",
                channelName = "Example Channel",
                existingTitles = listOf("Favorites", "Favorites - Example Channel"),
                fallbackTitle = "Imported playlist",
            ),
        )
    }

    @Test
    fun syntheticImportFallbackRunIsDetectedWithoutTouchingRealHistoryDates() {
        val videos = listOf(
            video("real-new", lastWatchedAt = 20_000L),
            video("fallback-1", lastWatchedAt = 10_000L),
            video("fallback-2", lastWatchedAt = 9_999L),
            video("fallback-3", lastWatchedAt = 9_998L),
            video("real-old", lastWatchedAt = 5_000L),
        )

        assertEquals(
            setOf("fallback-1", "fallback-2", "fallback-3"),
            syntheticHistoryFallbackIds(videos),
        )
    }

    private fun video(
        id: String,
        isDownloaded: Boolean = false,
        lastWatchedAt: Long = 0L,
    ) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "Now",
        duration = "1:00",
        isDownloaded = isDownloaded,
        lastWatchedAt = lastWatchedAt,
    )
}
