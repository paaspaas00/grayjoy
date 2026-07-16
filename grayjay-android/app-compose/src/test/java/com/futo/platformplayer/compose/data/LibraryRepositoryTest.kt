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

    private fun video(id: String, isDownloaded: Boolean = false) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "Now",
        duration = "1:00",
        isDownloaded = isDownloaded,
    )
}
