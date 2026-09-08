package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.VideoQualityUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class PlaybackSourceRefreshTest {
    @Test
    fun `refresh discards expired sources but preserves canonical identity and library metadata`() {
        val original = VideoUiModel(
            id = "video-1", title = "Video", creator = "Creator", metadata = "", duration = "10:00",
            sourceId = "example", contentUrl = "https://video.example/watch/1",
            playbackUrl = "https://cdn.example/expired.m3u8", playbackManifest = "expired manifest",
            playbackRequestHeaders = mapOf("Authorization" to "expired"),
            audioUrl = "https://cdn.example/expired-audio", audioRequestHeaders = mapOf("Token" to "expired"),
            qualityVariants = listOf(VideoQualityUiModel(720, "https://cdn.example/expired-720")),
            isDownloaded = true, playbackFromDownload = true, playbackAudioOnly = true,
            playbackCacheNamespace = "offline", audioCacheNamespace = "offline-audio",
            watchProgress = 0.5f, playlistNames = listOf("Playlist"), isLiked = true,
        )
        val fresh = original.onlinePlaybackInput()
        assertEquals(original.id, fresh.id)
        assertEquals(original.contentUrl, fresh.contentUrl)
        assertEquals(original.sourceId, fresh.sourceId)
        assertEquals(original.playlistNames, fresh.playlistNames)
        assertEquals(original.watchProgress, fresh.watchProgress)
        assertTrue(fresh.isLiked)
        assertTrue(fresh.playbackUrl.isEmpty())
        assertTrue(fresh.playbackManifest.isEmpty())
        assertTrue(fresh.audioUrl.isEmpty())
        assertTrue(fresh.playbackRequestHeaders.isEmpty())
        assertTrue(fresh.audioRequestHeaders.isEmpty())
        assertTrue(fresh.qualityVariants.isEmpty())
        assertTrue(fresh.playbackCacheNamespace.isEmpty())
        assertTrue(fresh.audioCacheNamespace.isEmpty())
        assertFalse(fresh.isDownloaded)
        assertFalse(fresh.playbackFromDownload)
        assertFalse(fresh.playbackAudioOnly)
        assertEquals("https://cdn.example/expired.m3u8", original.playbackUrl)
    }
}
