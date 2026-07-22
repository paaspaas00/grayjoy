package com.futo.platformplayer.compose.downloads

import androidx.media3.common.MimeTypes
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExportCompatibilityTest {
    @Test
    fun `video export rejects Opus in MP4 when audio is expected`() {
        assertFalse(
            isCompatibleExport(
                mediaType = DownloadMediaType.Video,
                expectsAudio = true,
                trackMimeTypes = listOf(MimeTypes.VIDEO_H264, MimeTypes.AUDIO_OPUS),
            ),
        )
    }

    @Test
    fun `video export accepts AVC with AAC`() {
        assertTrue(
            isCompatibleExport(
                mediaType = DownloadMediaType.Video,
                expectsAudio = true,
                trackMimeTypes = listOf(MimeTypes.VIDEO_H264, MimeTypes.AUDIO_AAC),
            ),
        )
    }

    @Test
    fun `silent video remains exportable when the source has no audio`() {
        assertTrue(
            isCompatibleExport(
                mediaType = DownloadMediaType.Video,
                expectsAudio = false,
                trackMimeTypes = listOf(MimeTypes.VIDEO_H264),
            ),
        )
    }

    @Test
    fun `audio export must contain AAC and no video`() {
        assertTrue(
            isCompatibleExport(
                mediaType = DownloadMediaType.Audio,
                expectsAudio = true,
                trackMimeTypes = listOf(MimeTypes.AUDIO_AAC),
            ),
        )
        assertFalse(
            isCompatibleExport(
                mediaType = DownloadMediaType.Audio,
                expectsAudio = true,
                trackMimeTypes = listOf(MimeTypes.AUDIO_OPUS),
            ),
        )
    }

    @Test
    fun `offline manifest and separate audio both require an audio track`() {
        assertTrue(
            video(audioUrl = "https://media.example/audio.webm")
                .expectsAudioInVideoExport(),
        )
        assertTrue(
            video(manifest = "<AdaptationSet mimeType=\"audio/mp4\" />")
                .expectsAudioInVideoExport(),
        )
    }

    private fun video(
        audioUrl: String = "",
        manifest: String = "",
    ) = VideoUiModel(
        id = "video",
        title = "Video",
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        audioUrl = audioUrl,
        playbackManifest = manifest,
    )
}
