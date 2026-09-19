package com.futo.platformplayer.compose

import android.content.pm.ActivityInfo
import android.content.Intent
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityOrientationTest {
    @Test
    fun `picture in picture aspect ratio follows video and clamps unsupported extremes`() {
        assertEquals(16 to 9, normalizedPictureInPictureAspectRatio(0, 0))
        assertEquals(4 to 3, normalizedPictureInPictureAspectRatio(4, 3))
        assertEquals(16 to 9, normalizedPictureInPictureAspectRatio(4_000, 1_000))
        assertEquals(9 to 16, normalizedPictureInPictureAspectRatio(1_000, 4_000))
    }

    @Test
    fun `picture in picture supports video and audio-only playback`() {
        assertTrue(
            shouldEnterPictureInPicture(
                enabled = true,
                hasVideo = true,
                audioOnly = false,
                isPlaying = true,
                isBuffering = false,
                isLoading = false,
            ),
        )
        assertFalse(
            shouldEnterPictureInPicture(
                enabled = false,
                hasVideo = true,
                audioOnly = false,
                isPlaying = true,
                isBuffering = false,
                isLoading = false,
            ),
        )
        assertTrue(
            shouldEnterPictureInPicture(
                enabled = true,
                hasVideo = true,
                audioOnly = true,
                isPlaying = true,
                isBuffering = false,
                isLoading = false,
            ),
        )
        assertTrue(
            shouldEnterPictureInPicture(
                enabled = true,
                hasVideo = true,
                audioOnly = false,
                isPlaying = false,
                isBuffering = false,
                isLoading = false,
            ),
        )
        assertFalse(
            shouldEnterPictureInPicture(
                enabled = true,
                hasVideo = true,
                audioOnly = false,
                isPlaying = true,
                isBuffering = false,
                isLoading = false,
                isCasting = true,
            ),
        )
    }

    @Test
    fun `fullscreen orientation is app controlled regardless of Android rotation lock`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            fullscreenPlayerOrientation(fullscreen = true, portraitVideo = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            fullscreenPlayerOrientation(fullscreen = true, portraitVideo = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            fullscreenPlayerOrientation(fullscreen = false, portraitVideo = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            fullscreenPlayerOrientation(
                fullscreen = false,
                portraitVideo = false,
                supportsResponsiveLayout = true,
            ),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            appOrientationForWindow(supportsResponsiveLayout = true),
        )
    }

    @Test
    fun `completed active download batch requests one toast`() {
        val downloading = DownloadUiModel(
            profileId = "main",
            videoId = "video",
            status = DownloadStatus.Downloading,
            activeMediaTypes = setOf(DownloadMediaType.Video),
        )
        val observed = updateDownloadCompletionBatch(emptySet(), listOf(downloading))
        assertFalse(observed.showCompletionToast)

        val completed = downloading.copy(
            status = DownloadStatus.Completed,
            activeMediaTypes = emptySet(),
            completedMediaTypes = setOf(DownloadMediaType.Video),
        )
        val finished = updateDownloadCompletionBatch(observed.pending, listOf(completed))
        assertTrue(finished.showCompletionToast)
        assertTrue(finished.pending.isEmpty())

        assertFalse(
            updateDownloadCompletionBatch(finished.pending, listOf(completed)).showCompletionToast,
        )
    }

    @Test
    fun `failed download batch does not request completion toast`() {
        val downloading = DownloadUiModel(
            profileId = "main",
            videoId = "video",
            status = DownloadStatus.Downloading,
        )
        val observed = updateDownloadCompletionBatch(emptySet(), listOf(downloading))
        val failed = downloading.copy(
            status = DownloadStatus.Failed,
            failedMediaTypes = setOf(DownloadMediaType.Video),
        )

        assertFalse(updateDownloadCompletionBatch(observed.pending, listOf(failed)).showCompletionToast)
    }

    @Test
    fun `view and shared text intents extract a web content url`() {
        val videoUrl = "https://www.youtube.com/watch?v=abc123"
        assertEquals(
            videoUrl,
            externalContentUrl(Intent.ACTION_VIEW, videoUrl, null),
        )
        assertEquals(
            videoUrl,
            externalContentUrl(Intent.ACTION_SEND, null, "Watch this: $videoUrl"),
        )
    }

    @Test
    fun `non web intents do not become content links`() {
        assertNull(externalContentUrl(Intent.ACTION_VIEW, "grayjay://plugin/example", null))
        assertNull(externalContentUrl(Intent.ACTION_SEND, null, "No link here"))
    }
}
