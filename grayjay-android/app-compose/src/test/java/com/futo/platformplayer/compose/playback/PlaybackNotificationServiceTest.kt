package com.futo.platformplayer.compose.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNotificationServiceTest {
    @Test
    fun `transient Media3 cancellation restores an attached playback notification`() {
        assertTrue(
            shouldRestorePlaybackNotification(
                hasAttachment = true,
                closingFromNotification = false,
                dismissedByUser = false,
            ),
        )
    }

    @Test
    fun `user dismissal and playback shutdown never recreate notification`() {
        assertFalse(
            shouldRestorePlaybackNotification(
                hasAttachment = true,
                closingFromNotification = false,
                dismissedByUser = true,
            ),
        )
        assertFalse(
            shouldRestorePlaybackNotification(
                hasAttachment = true,
                closingFromNotification = true,
                dismissedByUser = false,
            ),
        )
        assertFalse(
            shouldRestorePlaybackNotification(
                hasAttachment = false,
                closingFromNotification = false,
                dismissedByUser = false,
            ),
        )
    }
}
