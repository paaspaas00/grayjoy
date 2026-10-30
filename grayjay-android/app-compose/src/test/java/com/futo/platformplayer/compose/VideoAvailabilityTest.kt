package com.futo.platformplayer.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAvailabilityTest {
    @Test fun pendingLiveInSecondaryEngineIsNotPermanentlyDeleted() {
        val error = IllegalStateException("Both engines failed", IllegalArgumentException("Video unavailable"))
        error.addSuppressed(IllegalStateException("LIVE_STREAM_OFFLINE: This live event will begin in a few moments."))
        assertFalse(isPermanentlyUnavailableVideo(error, "An event (live)", true))
    }
    @Test fun explicitDeletionInSecondaryEngineIsDetected() {
        val error = IllegalStateException("Both engines failed", IllegalArgumentException("No stream"))
        error.addSuppressed(IllegalStateException("This video has been removed"))
        assertTrue(isPermanentlyUnavailableVideo(error))
    }
    @Test fun cyclicErrorsDoNotHangAvailabilityClassification() {
        val first = IllegalStateException("Network timeout")
        val second = IllegalStateException("Wrapped error")
        first.initCause(second); second.initCause(first)
        assertFalse(isPermanentlyUnavailableVideo(first))
    }
    @Test
    fun `removed and private video errors are permanent`() {
        assertTrue(isPermanentlyUnavailableVideo(IllegalStateException("Video unavailable")))
        assertTrue(
            isPermanentlyUnavailableVideo(
                RuntimeException("wrapper", IllegalStateException("This video has been removed")),
            ),
        )
        assertTrue(isPermanentlyUnavailableVideo(IllegalStateException("Private video")))
        assertFalse(
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
