package com.futo.platformplayer.compose.ui

import androidx.compose.ui.geometry.Rect
import com.futo.platformplayer.compose.ui.screens.channelPageLinks
import org.junit.Assert.*
import org.junit.Test

class BrowseMorphPolicyTest {
    @Test fun aPageMorphOnlyAcceptsItsOwnRouteAndMeasurements() {
        val motion = PlayerCardTransition { 0L }
        val source = CardMorphSource(VideoUiModel("same", "Album", "", "", ""), true,
            destination = "playlist:same", artworkCornerDp = 74f)
        source.capture("surface", Rect(0f, 100f, 150f, 300f))
        source.capture("art", Rect(0f, 100f, 150f, 250f))
        motion.arm(source); motion.beginDestination("channel:same"); assertFalse(motion.active)
        motion.arm(source); motion.beginDestination("playlist:same"); assertTrue(motion.active)
        motion.capture("same", "surface", Rect(0f, 0f, 400f, 900f), 0f)
        assertFalse(motion.freeze())
        motion.captureDestination("playlist:same", "surface", Rect(0f, 0f, 400f, 900f), 0f)
        motion.captureDestination("playlist:same", "art", Rect(20f, 30f, 180f, 190f), 12f)
        assertTrue(motion.freeze())
        assertEquals(74f, motion.request!!.nodes.getValue("art").cornerDp)
        motion.clear(); assertFalse(motion.active)
    }
    @Test fun spotifyArtistSelfLinksDisappearButOtherLinksAndOtherSourcesRemain() {
        val links = linkedMapOf("Spotify" to "https://open.spotify.com/artist/a", "Web" to "https://artist.example",
            "Album" to "https://spotify.com/album/a", "Not the same host" to "https://spotify.com.example/path")
        val channel = ChannelUiModel("a", "Artist", "spotify", "Spotify", 0, "", "", links = links)
        assertEquals(setOf("Web", "Not the same host"), channelPageLinks(channel).keys)
        assertEquals(links, channelPageLinks(channel.copy(sourceId = "youtube")))
    }
    @Test fun anExplicitShortDoesNotNeedAResolvedVideoSizeToChoosePortrait() {
        assertTrue(usePortraitPlayerFullscreen(VideoUiModel("short", "Short", "Creator", "", "", isShort = true), PlaybackUiState()))
        assertFalse(usePortraitPlayerFullscreen(VideoUiModel("video", "Video", "Creator", "", ""), PlaybackUiState()))
    }
    @Test fun ordinaryPortraitVideosAreNotTreatedAsShortCards() {
        val video = VideoUiModel("video", "Video", "Creator", "", "")
        assertFalse(isExplicitShort(video))
        assertTrue(isExplicitShort(video.copy(contentUrl = "https://www.youtube.com/shorts/id")))
        assertTrue(usePortraitPlayerFullscreen(video, PlaybackUiState(currentVideoId = video.id, currentVideoWidth = 720, currentVideoHeight = 1280)))
    }
}
