package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlaybackDescriptorTest {
    @Test
    fun completedAudioDownloadBecomesOfflinePrimaryMedia() {
        val descriptor = video().withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Audio,
                    name = "audio",
                    uri = "https://media.example/audio",
                    mimeType = "audio/mp4",
                ),
            ),
        )

        assertNotNull(descriptor)
        assertTrue(descriptor!!.playbackFromDownload)
        assertTrue(descriptor.isDownloaded)
        assertEquals("https://media.example/audio", descriptor.playbackUrl)
        assertEquals("audio/mp4", descriptor.playbackMimeType)
        assertEquals("", descriptor.audioUrl)
    }

    @Test
    fun completedVideoDownloadIsPreferredOverSeparateAudioDownload() {
        val descriptor = video().withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "video",
                    expectedPartCount = 2,
                    uri = "https://media.example/video",
                    mimeType = "video/mp4",
                ),
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "audio",
                    expectedPartCount = 2,
                    uri = "https://media.example/video-audio",
                ),
                part(
                    mediaType = DownloadMediaType.Audio,
                    name = "audio",
                    uri = "https://media.example/audio-only",
                ),
            ),
        )

        assertNotNull(descriptor)
        assertEquals("https://media.example/video", descriptor!!.playbackUrl)
        assertEquals("https://media.example/video-audio", descriptor.audioUrl)
    }

    @Test
    fun incompleteVideoDownloadFallsBackToCompletedAudioDownload() {
        val descriptor = video().withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "video",
                    expectedPartCount = 2,
                    uri = "https://media.example/incomplete-video",
                ),
                part(
                    mediaType = DownloadMediaType.Audio,
                    name = "audio",
                    uri = "https://media.example/audio",
                ),
            ),
        )

        assertNotNull(descriptor)
        assertEquals("https://media.example/audio", descriptor!!.playbackUrl)
    }

    @Test
    fun incompleteDownloadDoesNotCreateCacheOnlyPlayback() {
        val descriptor = video().withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "video",
                    expectedPartCount = 2,
                    uri = "https://media.example/incomplete-video",
                ),
            ),
        )

        assertNull(descriptor)
    }

    private fun video() = VideoUiModel(
        id = "video",
        title = "Video",
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        isDownloaded = true,
    )

    private fun part(
        mediaType: DownloadMediaType,
        name: String,
        uri: String,
        expectedPartCount: Int = 1,
        mimeType: String = "",
    ) = OfflinePlaybackPart(
        mediaType = mediaType,
        name = name,
        expectedPartCount = expectedPartCount,
        uri = uri,
        mimeType = mimeType,
    )
}
