package com.futo.platformplayer.compose.playback

import org.junit.Assert.*
import org.junit.Test

class SeekPreviewPauseTest {
    private val key = SeekPreviewIdentity("video", "profile", 1, 2, false)
    @Test fun restoresPlayingButNeverStartsAnAlreadyPausedVideo() {
        for (playing in listOf(true, false)) {
            val guard = SeekPreviewPause()
            assertEquals(playing, guard.begin(key, playing))
            assertFalse(guard.begin(key, false))
            assertEquals(playing, guard.finish("video", key))
            assertFalse(guard.finish("video", key))
        }
    }
    @Test fun cannotResumeAnotherVideoProfileRequestOrCastSession() {
        for (changed in listOf(key.copy(videoId = "other"), key.copy(profileId = "other"),
            key.copy(openGeneration = 2), key.copy(queueGeneration = 3), key.copy(casting = true))) {
            val guard = SeekPreviewPause()
            guard.begin(key, true)
            assertFalse(guard.finish("video", changed))
        }
    }
    @Test fun oldSurfaceDisposalCannotFinishANewVideosScrub() {
        val guard = SeekPreviewPause()
        val next = key.copy(videoId = "next")
        guard.begin(key, true)
        guard.begin(next, true)
        assertFalse(guard.finish("video", next))
        assertTrue(guard.finish("next", next))
    }
    @Test fun explicitPlaybackChangeCancelsAutomaticResume() {
        val guard = SeekPreviewPause()
        guard.begin(key, true)
        guard.cancel()
        assertFalse(guard.finish("video", key))
    }
}
