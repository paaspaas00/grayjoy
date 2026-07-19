package com.futo.platformplayer.compose

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
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
    fun `landscape sensor ranges select fullscreen posture`() {
        listOf(60, 90, 120, 240, 270, 300).forEach { degrees ->
            assertEquals(true, physicalLandscapeAt(degrees))
        }
    }

    @Test
    fun `upright and reverse portrait ranges select portrait posture`() {
        listOf(0, 30, 150, 180, 210, 330, 359).forEach { degrees ->
            assertEquals(false, physicalLandscapeAt(degrees))
        }
    }

    @Test
    fun `diagonal and unknown readings preserve the previous posture`() {
        listOf(31, 45, 59, 121, 225, 301, 329).forEach { degrees ->
            assertNull(physicalLandscapeAt(degrees))
        }
        assertNull(physicalLandscapeAt(OrientationEventListener.ORIENTATION_UNKNOWN))
    }

    @Test
    fun `fullscreen posture ignores the sensor when Android auto rotate is disabled`() {
        assertEquals(false, automaticFullscreenPosture(autoRotateEnabled = false, orientation = 90))
        assertEquals(true, automaticFullscreenPosture(autoRotateEnabled = true, orientation = 90))
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
}
