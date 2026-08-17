package com.futo.platformplayer.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAvailabilityTest {
    @Test
    fun `removed and private video errors are permanent`() {
        assertTrue(isPermanentlyUnavailableVideo(IllegalStateException("Video unavailable")))
        assertTrue(
            isPermanentlyUnavailableVideo(
                RuntimeException("wrapper", IllegalStateException("This video has been removed")),
            ),
        )
        assertTrue(isPermanentlyUnavailableVideo(IllegalStateException("Private video")))
        assertTrue(
            isPermanentlyUnavailableVideo(
                IllegalStateException("The plugin returned no supported video or audio stream."),
                videoTitle = "Resistori #3 (live)",
            ),
        )
    }

    @Test
    fun `network and login failures are not marked unavailable`() {
        assertFalse(isPermanentlyUnavailableVideo(IllegalStateException("Network timeout")))
        assertFalse(isPermanentlyUnavailableVideo(IllegalStateException("Login required")))
        assertFalse(
            isPermanentlyUnavailableVideo(
                IllegalStateException("The plugin returned no supported video or audio stream."),
                videoTitle = "Ordinary upload",
            ),
        )
    }
}
