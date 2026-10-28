package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.data.*
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.onboarding.*
import org.junit.Assert.*
import org.junit.Test

class MusicWorkflowPolicyTest {
    @Test fun spotifyUsesIntermediateVideoButOtherSourcesKeepTheirPreference() {
        assertEquals(480, playbackVideoHeightTarget("spotify", 2160))
        assertEquals(360, playbackVideoHeightTarget("Spotify", 360))
        assertEquals(1080, playbackVideoHeightTarget("youtube", 1080))
        assertEquals(480, playbackVideoHeightTarget("youtube", 1080, musicProfile = true))
    }
    @Test fun musicHelpExcludesVideoOnlyWorkflowsAndIncludesThreeMusicGuides() {
        val topics = applicableHelpTopics(true, true, false)
        assertEquals(3, topics.count { it.name.startsWith("Spotify") })
        assertFalse(topics.contains(HelpTopic.Follow))
        assertFalse(topics.contains(HelpTopic.Download))
        assertFalse(applicableHelpTopics(false, false, true).any { it.name.startsWith("Spotify") })
    }
    @Test fun likedPlaylistIdentityDoesNotAcceptLookalikeHostsOrOtherLists() {
        assertTrue(isYoutubeLikedPlaylistKey(YOUTUBE_LIKED_LOCAL_ID))
        assertTrue(isYoutubeLikedPlaylistKey("https://m.youtube.com/playlist?list=LL&foo=bar"))
        assertFalse(isYoutubeLikedPlaylistKey("https://notyoutube.com/playlist?list=LL"))
        assertFalse(isYoutubeLikedPlaylistKey("https://www.youtube.com/playlist?list=LLother"))
        assertTrue(isYoutubeLikedPlaylist(PlaylistUiModel("imported", "Video piaciuti", "", emptyList()), "Liked videos"))
    }
}
