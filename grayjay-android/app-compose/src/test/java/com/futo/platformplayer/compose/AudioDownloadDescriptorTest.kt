package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDownloadDescriptorTest {
    @Test
    fun muxedProgressiveSourceIsValidAudioFallback() {
        val descriptor = video(
            playbackUrl = "https://media.example/muxed.mp4",
            playbackMimeType = "video/mp4",
            playbackHasMuxedAudio = true,
        ).asAudioDownloadDescriptor(preferredBitrate = null)

        assertEquals("https://media.example/muxed.mp4", descriptor.playbackUrl)
        assertEquals("video/mp4", descriptor.playbackMimeType)
        assertTrue(descriptor.audioUrl.isEmpty())
    }

    @Test
    fun muxedAdaptiveFallbackPreservesManifest() {
        val manifest = "<MPD>muxed</MPD>"
        val descriptor = video(
            playbackUrl = "https://media.example/muxed.mpd",
            playbackMimeType = "application/dash+xml",
            playbackManifest = manifest,
            playbackHasMuxedAudio = true,
        ).asAudioDownloadDescriptor(preferredBitrate = null)

        assertEquals(manifest, descriptor.playbackManifest)
    }

    @Test
    fun unmuxedVideoOnlySourceIsNeverUsedAsAudio() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            video(
                playbackUrl = "https://media.example/video-only.mp4",
                playbackMimeType = "video/mp4",
                playbackHasMuxedAudio = false,
            ).asAudioDownloadDescriptor(preferredBitrate = null)
        }

        assertEquals("This source returned no downloadable audio.", error.message)
    }

    private fun video(
        playbackUrl: String,
        playbackMimeType: String,
        playbackManifest: String = "",
        playbackHasMuxedAudio: Boolean,
    ) = VideoUiModel(
        id = "video",
        title = "Video",
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        playbackUrl = playbackUrl,
        playbackMimeType = playbackMimeType,
        playbackManifest = playbackManifest,
        playbackHasMuxedAudio = playbackHasMuxedAudio,
    )
}
