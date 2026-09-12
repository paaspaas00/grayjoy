package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class DownloadLibraryReconciliationTest {
    private fun video() = VideoUiModel(
        id = "video", title = "Saved video", creator = "Creator", metadata = "", duration = "1:00",
        isDownloaded = true, isWatchLater = true, lastWatchedAt = 123L, watchProgress = 0.5f,
        playlistNames = listOf("Playlist"), playbackUrl = "https://example.org/expired",
        playbackManifest = "<MPD/>",
    )

    @Test fun removedOrIncompleteDownloadDropsOnlyItsPlaybackDescriptor() {
        val updated = reconcileVideoDownloadState(video(), emptySet())
        assertFalse(updated.isDownloaded)
        assertTrue(updated.playbackUrl.isEmpty())
        assertTrue(updated.playbackManifest.isEmpty())
        assertTrue(updated.isWatchLater)
        assertEquals(123L, updated.lastWatchedAt)
        assertEquals(0.5f, updated.watchProgress)
        assertEquals(listOf("Playlist"), updated.playlistNames)
    }

    @Test fun completedDownloadKeepsOfflineDescriptorAndDoesNotAllocateANewModel() {
        val original = video()
        assertSame(original, reconcileVideoDownloadState(original, setOf(original.id)))
    }
}
