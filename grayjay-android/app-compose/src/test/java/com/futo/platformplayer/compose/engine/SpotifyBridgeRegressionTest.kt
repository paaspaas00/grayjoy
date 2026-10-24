package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class SpotifyBridgeRegressionTest {
    private val track = VideoUiModel("spotify", "Killing In The Name", "Rage Against The Machine", "", "5:13", sourceId = "spotify")
    @Test fun matchingArtistAndDurationAloneCannotDownloadTheWrongSong() {
        val wrong = track.copy(id = "youtube", title = "Bulls On Parade", sourceId = "youtube")
        assertNull(selectSpotifyYoutubeCandidate(track, listOf(wrong)))
    }
    @Test fun officialCandidatePreservesTitleAndArtistMatch() {
        val match = track.copy(id = "youtube", title = "Rage Against The Machine - Killing In The Name (Official Audio)", creator = "Music", sourceId = "youtube")
        assertEquals(match, selectSpotifyYoutubeCandidate(track, listOf(match)))
        assertNull(selectSpotifyYoutubeCandidate(track, listOf(match.copy(isAvailable = false))))
        assertEquals(2, spotifyYoutubeSearchQueries(track).size)
    }
    @Test fun stalledPlaybackEscalatesQuicklyButNewBytesResetTheDeadline() {
        val guard = PlaybackStallGuard()
        assertFalse(guard.stalled("v", 30_000, 30_000, 0))
        assertFalse(guard.stalled("v", 30_000, 30_000, 3999))
        assertTrue(guard.stalled("v", 30_000, 30_000, 4000))
        assertFalse(guard.stalled("v", 30_000, 32_000, 4001))
        assertFalse(guard.stalled("v", 30_000, 32_000, 6000))
    }
    @Test fun aRecoveredPrimaryAvoidsAnUnnecessaryAudibleHandoff() {
        assertTrue(primaryPlaybackRecovered(true, true, false, 1000, 1000))
        assertTrue(primaryPlaybackRecovered(false, true, false, 1000, 2500))
        assertFalse(primaryPlaybackRecovered(false, true, false, 1000, 1000))
        assertFalse(primaryPlaybackRecovered(true, true, true, 1000, 2500))
        assertFalse(primaryPlaybackRecovered(true, false, false, 1000, 2500))
    }
}
