package com.futo.platformplayer.compose.downloads

import androidx.media3.common.StreamKey
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.SubtitleUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlaybackDescriptorTest {
    @Test
    fun intentionalRemovalNeverLooksLikeFailedCompletedDownload() {
        assertEquals(
            DownloadStatus.Removing,
            aggregateDownloadStatus(
                removing = true,
                hasFailedRequest = false,
                hasRemovingRequest = false,
                validatedComplete = false,
                media3Complete = true,
                hasDownloadingRequest = false,
                hasStoppedRequest = false,
            ),
        )
    }

    @Test
    fun unmetNetworkRequirementLooksPausedRatherThanFailed() {
        assertEquals(
            DownloadStatus.Paused,
            aggregateDownloadStatus(
                removing = false,
                hasFailedRequest = false,
                hasRemovingRequest = false,
                validatedComplete = false,
                media3Complete = false,
                hasDownloadingRequest = true,
                hasStoppedRequest = false,
                waitingForRequirements = true,
            ),
        )
    }

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
        assertTrue(descriptor.playbackAudioOnly)
        assertTrue(descriptor.isDownloaded)
        assertEquals("https://media.example/audio", descriptor.playbackUrl)
        assertEquals("audio/mp4", descriptor.playbackMimeType)
        assertEquals("", descriptor.audioUrl)
    }

    @Test
    fun completedRawDashAudioKeepsItsOfflineManifest() {
        val manifest = "<MPD mediaPresentationDuration=\"PT1M\" />"
        val descriptor = video().withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Audio,
                    name = "audio",
                    uri = "https://media.example/audio-manifest",
                    mimeType = "application/dash+xml",
                    rawManifest = manifest,
                ),
            ),
        )

        assertNotNull(descriptor)
        assertEquals(manifest, descriptor!!.playbackManifest)
        assertEquals("application/dash+xml", descriptor.playbackMimeType)
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
        assertFalse(descriptor.playbackAudioOnly)
    }

    @Test
    fun adaptiveSelectionsAreRetainedForCacheOnlyPlayback() {
        val videoKey = StreamKey(0, 2, 4)
        val audioKey = StreamKey(0, 1, 3)
        val descriptor = video().withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "video",
                    expectedPartCount = 2,
                    uri = "https://media.example/master.m3u8",
                    streamKeys = listOf(videoKey),
                ),
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "audio",
                    expectedPartCount = 2,
                    uri = "https://media.example/audio.m3u8",
                    streamKeys = listOf(audioKey),
                ),
            ),
        )

        assertNotNull(descriptor)
        assertEquals(listOf(videoKey), descriptor!!.playbackStreamKeys)
        assertEquals(listOf(audioKey), descriptor.audioStreamKeys)
    }

    @Test
    fun completedSubtitleKeepsItsOwnOfflineCacheIdentity() {
        val source = video().copy(
            subtitleTracks = listOf(
                SubtitleUiModel(
                    name = "English",
                    language = "en",
                    uri = "https://media.example/captions.vtt",
                    mimeType = "text/vtt",
                ),
            ),
        )
        val descriptor = source.withOfflinePlayback(
            listOf(
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "video",
                    expectedPartCount = 2,
                    uri = "https://media.example/video",
                ),
                part(
                    mediaType = DownloadMediaType.Video,
                    name = "subtitle-0",
                    expectedPartCount = 2,
                    uri = "https://media.example/captions.vtt",
                    mimeType = "text/vtt",
                    headers = mapOf("Referer" to "https://media.example/"),
                    cacheNamespace = "subtitle-request",
                ),
            ),
        )

        assertNotNull(descriptor)
        val subtitle = descriptor!!.subtitleTracks.single()
        assertEquals("English", subtitle.name)
        assertEquals("subtitle-request", subtitle.cacheNamespace)
        assertEquals(
            mapOf("Referer" to "https://media.example/"),
            subtitle.requestHeaders,
        )
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
        assertTrue(descriptor.playbackAudioOnly)
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

    @Test
    fun completedTransferIsPromotedOnlyAfterEveryOutputValidates() {
        assertTrue(
            isValidatedCompletedDownload(
                listOf(
                    completionPart("video", expectedPartCount = 2),
                    completionPart("audio", expectedPartCount = 2),
                ),
            ),
        )
    }

    @Test
    fun partialTransferIsNeverPromotedToOfflineMedia() {
        assertTrue(
            !isValidatedCompletedDownload(
                listOf(completionPart("video", expectedPartCount = 2)),
            ),
        )
    }

    @Test
    fun missingOrEmptyCacheResourceFailsValidation() {
        assertTrue(
            !isValidatedCompletedDownload(
                listOf(completionPart("video", bytesDownloaded = 0L)),
            ),
        )
        assertTrue(
            !isValidatedCompletedDownload(
                listOf(completionPart("video", rootResourceCached = false)),
            ),
        )
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
        rawManifest: String = "",
        streamKeys: List<StreamKey> = emptyList(),
        headers: Map<String, String> = emptyMap(),
        cacheNamespace: String = "",
    ) = OfflinePlaybackPart(
        mediaType = mediaType,
        name = name,
        expectedPartCount = expectedPartCount,
        uri = uri,
        mimeType = mimeType,
        rawManifest = rawManifest,
        streamKeys = streamKeys,
        headers = headers,
        cacheNamespace = cacheNamespace,
    )

    private fun completionPart(
        name: String,
        expectedPartCount: Int = 1,
        completed: Boolean = true,
        bytesDownloaded: Long = 1L,
        rootResourceCached: Boolean = true,
    ) = DownloadCompletionPart(
        name = name,
        expectedPartCount = expectedPartCount,
        completed = completed,
        bytesDownloaded = bytesDownloaded,
        rootResourceCached = rootResourceCached,
    )
}
