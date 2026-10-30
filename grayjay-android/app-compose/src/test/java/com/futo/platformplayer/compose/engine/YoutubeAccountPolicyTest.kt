package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.data.canShareYoutubePlaybackAccount
import com.futo.platformplayer.compose.ui.*
import org.junit.Assert.*
import org.junit.Test

class YoutubeAccountPolicyTest {
    private val spotify = ProfileUiModel("spotify-profile", "Spotify", ProfileProtection.None, sourceId = "spotify")
    private val youtube = ProfileUiModel("youtube-profile", "YouTube", ProfileProtection.None, sourceId = "youtube")
    @Test fun authenticatedPlaybackUsesTheCapableEngineWhileAnonymousPreferenceIsUnchanged() {
        assertEquals(YoutubePlaybackResolver.Grayjay, youtubePlaybackResolverOrder(true, true).first())
        assertEquals(YoutubePlaybackResolver.NewPipe, youtubePlaybackResolverOrder(true, false).first())
    }
    @Test fun protectedPrivateMissingAndUnrelatedProfilesCannotBorrowAnAccount() {
        assertTrue(canShareYoutubePlaybackAccount(spotify, youtube))
        assertFalse(canShareYoutubePlaybackAccount(spotify, youtube.copy(protection = ProfileProtection.Pin)))
        assertFalse(canShareYoutubePlaybackAccount(spotify.copy(protection = ProfileProtection.DeviceCredential), youtube))
        assertFalse(canShareYoutubePlaybackAccount(spotify, youtube.copy(id = "private")))
        assertFalse(canShareYoutubePlaybackAccount(spotify, null))
        assertFalse(canShareYoutubePlaybackAccount(youtube, spotify))
        assertTrue(canShareYoutubePlaybackAccount(youtube.copy(name = "Music", sourceId = ""), spotify, spotifyOnly = true))
    }
    @Test fun loginChallengeDetectionHandlesFallbackCausesAndDoesNotMaskNetworkErrors() {
        assertTrue(isYoutubeLoginChallenge(IllegalStateException("Both failed", RuntimeException("LOGIN_REQUIRED"))))
        assertTrue(isYoutubeLoginChallenge(Exception("failed").apply { addSuppressed(Exception("Sign in to confirm you are not a bot")) }))
        assertFalse(isYoutubeLoginChallenge(java.io.IOException("Connection timed out")))
    }
}
