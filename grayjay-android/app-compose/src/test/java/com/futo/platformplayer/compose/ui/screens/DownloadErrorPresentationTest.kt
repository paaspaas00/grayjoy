package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorPresentationTest {
    @Test
    fun pluginAndEngineInternalsAreNotShownToUsers() {
        assertTrue(
            isTechnicalDownloadError(
                "[Source] Error: Failed to get video details (2686)[8-9]",
            ),
        )
        assertTrue(
            isTechnicalDownloadError(
                "Both YouTube playback engines failed. NewPipe: JSON response is too short; " +
                    "Grayjay: No token Id provided for botguard",
            ),
        )
        assertTrue(isTechnicalDownloadError("java.io.IOException: response code 403"))
    }

    @Test
    fun actionableMessagesRemainVisible() {
        assertFalse(isTechnicalDownloadError("Waiting for the network"))
        assertFalse(
            isTechnicalDownloadError(
                "Not enough free storage; download paused safely",
            ),
        )
        assertFalse(
            isTechnicalDownloadError(
                "Retry or update the source.",
            ),
        )
    }
}
