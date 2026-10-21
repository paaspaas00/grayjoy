package com.futo.platformplayer.compose.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNotificationServiceTest {
    @Test fun idleAndEmptyPlayersDoNotCreateAnEndlessRestoreLoop() {
        assertFalse(canRebuildPlaybackNotification(0, androidx.media3.common.Player.STATE_READY))
        assertFalse(canRebuildPlaybackNotification(1, androidx.media3.common.Player.STATE_IDLE))
        assertTrue(canRebuildPlaybackNotification(1, androidx.media3.common.Player.STATE_BUFFERING))
        assertTrue(canRebuildPlaybackNotification(1, androidx.media3.common.Player.STATE_READY))
        assertTrue(canRebuildPlaybackNotification(1, androidx.media3.common.Player.STATE_ENDED))
    }

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
