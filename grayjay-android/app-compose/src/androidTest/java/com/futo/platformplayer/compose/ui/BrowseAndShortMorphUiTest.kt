package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.LocalVideoFrameReadiness
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class BrowseAndShortMorphUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var fullscreenPresentation = false to false
    private val art get() = "android.resource://${InstrumentationRegistry.getInstrumentation().context.packageName}/drawable/audit_earth"
    @Before fun setup() = rule.runOnUiThread {
        rule.activity.enableEdgeToEdge()
        player = ExoPlayer.Builder(rule.activity.applicationContext).build()
    }
    @After fun cleanup() = rule.runOnUiThread { player.release() }

    private fun musicHome() {
        var state by mutableStateOf(GrayjayUiState(sources = listOf(SourceUiModel("spotify", "Spotify", "", 0L, true)),
            home = HomeUiState(musicSections = listOf(MusicHomeSection("browse", "Music", listOf(
                MusicHomeItem("playlist-one", "My playlist", "playlist", art),
                MusicHomeItem("artist-one", "An artist", "artist", art)))))))
        val actions = GrayjayAppActions().apply {
            onLoadRemotePlaylist = { playlist -> state = state.copy(remotePlaylistDetail = RemotePlaylistDetailUiState(playlist = playlist, isLoading = true)) }
            onLoadChannel = { channel -> state = state.copy(channelDetail = ChannelDetailUiState(channelId = channel.id,
                channel = channel.copy(links = mapOf("Spotify" to "https://open.spotify.com/artist/artist-one", "Website" to "https://artist.example")),
                isLoading = true, supportsPlaylists = true)) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true, musicProfile = true) { GrayjayApp(state, player, actions) } }
    }
    private fun clickFrozen(tag: String): Rect {
        val node = rule.onNodeWithTag(tag).assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        val click = node.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnUiThread { click() }
        return bounds
    }
    private fun sampleMorph(name: String, source: Rect, expanding: Boolean, part: String = "surface"): List<Rect> {
        var time = 0L
        val surfaces = mutableListOf<Rect>()
        for (frame in listOf(48L, 96L, 160L, 240L, 320L, 480L, 720L)) {
            rule.mainClock.advanceTimeBy(frame - time); time = frame
            rule.waitForIdle()
            rule.onAllNodesWithTag("card-player-morph-$part").fetchSemanticsNodes().firstOrNull()?.let { surfaces += it.boundsInRoot }
            if (frame == 160L) capture("$name-middle")
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
        assertTrue("A morph must contain intermediate surfaces", surfaces.size >= 2)
        if (expanding) assertTrue("The source card must grow", surfaces.any { it.height > source.height + 30f })
        capture("$name-complete")
        return surfaces
    }
    @Test fun aHomePlaylistExpandsIntoItsPlaylistAndBackRestoresHome() {
        musicHome()
        val source = clickFrozen("music-home-playlist:playlist-one")
        sampleMorph("browse-playlist", source, expanding = true)
        rule.onNodeWithTag("remote-playlist-playlist-one").assertIsDisplayed()
        rule.onNodeWithTag("browse-back").performClick()
        rule.onNodeWithTag("music-home-playlist:playlist-one").assertIsDisplayed()
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
    }
    @Test fun aHomeArtistMorphsItsCircularArtAndHasNoSpotifySelfLink() {
        musicHome()
        val source = clickFrozen("music-home-channel:artist-one")
        sampleMorph("browse-artist", source, expanding = true)
        rule.onNodeWithTag("channel-header-avatar").assertIsDisplayed()
        if (rule.onAllNodesWithTag("channel-more-actions").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithTag("channel-more-actions").performClick()
        rule.onNodeWithTag("channel-link-https://open.spotify.com/artist/artist-one").assertDoesNotExist()
        rule.onAllNodesWithText("Spotify").filter(hasClickAction()).assertCountEquals(0)
        rule.onNodeWithText("Website").assertExists()
    }
    @Test fun backDuringAHomePageMorphDoesNotLeaveAnInvisibleInputBlocker() {
        musicHome()
        clickFrozen("music-home-playlist:playlist-one")
        rule.mainClock.advanceTimeBy(96)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(800); rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
        rule.onNodeWithTag("music-home-playlist:playlist-one").assertIsDisplayed().performClick()
        rule.onNodeWithTag("remote-playlist-playlist-one").assertIsDisplayed()
    }
    private fun shortHome(brainrot: Boolean, loseMetadata: Boolean = false): VideoUiModel {
        val video = VideoUiModel("short-one", "A short video", "Creator", "", "0:25", isShort = true, thumbnailUrl = art)
        var state by mutableStateOf(GrayjayUiState(sources = listOf(SourceUiModel("youtube", "YouTube", "", 0L, true)),
            videos = listOf(video), home = HomeUiState(selectedFeed = HomeFeedType.Shorts, videos = listOf(video)),
            brainrotShortsEnabled = brainrot))
        val actions = GrayjayAppActions().apply {
            onOpenVideo = { state = state.copy(nowPlaying = NowPlayingUiState(video = video.copy(isShort = !loseMetadata), isLoadingExtras = true),
                playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id), durationMs = 25_000)) }
            onFullscreenPresentationChanged = { full, portrait -> fullscreenPresentation = full to portrait }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            CompositionLocalProvider(LocalVideoFrameReadiness provides { false }) { GrayjayApp(state, player, actions) }
        } }
        return video
    }
    @Test fun aShortCardMorphsIntoPortraitFullscreenWithoutBrainrot() {
        val video = shortHome(false)
        val source = clickFrozen("video-card-${video.id}")
        sampleMorph("short-card-entry", source, expanding = true)
        val fullscreen = rule.onNodeWithTag("fullscreen-player").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(fullscreen.height > fullscreen.width)
        rule.onNodeWithTag("now-playing-header").assertDoesNotExist()
    }
    @Test fun missingBackendDimensionsCannotRotateOrCancelThePortraitEntry() {
        val video = shortHome(false, loseMetadata = true)
        val source = clickFrozen("video-card-${video.id}")
        sampleMorph("short-missing-metadata", source, expanding = true)
        rule.runOnIdle { assertEquals(true to true, fullscreenPresentation) }
        rule.onNodeWithTag("fullscreen-player").assertIsDisplayed()
    }
    @Test fun portraitFullscreenMorphsBackToNormalNowPlayingWithLoadingContent() {
        val video = shortHome(false)
        val source = clickFrozen("video-card-${video.id}")
        sampleMorph("short-before-exit", source, expanding = true)
        clickFrozen("player-fullscreen")
        // PixelCopy has a real-time deadline, independent of the paused Compose animation clock.
        android.os.SystemClock.sleep(100)
        val surfaces = sampleMorph("short-exit", source, expanding = false, part = "art")
        assertTrue("The fullscreen surface must contract", surfaces.first().height > surfaces.last().height + 30f)
        rule.onNodeWithTag("fullscreen-player").assertDoesNotExist()
        rule.onNodeWithTag("now-playing-header").assertIsDisplayed()
        rule.onNodeWithTag("video-info-loading").assertExists()
    }
    @Test fun androidBackAlsoMorphsOutOfTheBrainrotPlayer() {
        val video = shortHome(true)
        val source = clickFrozen("video-card-${video.id}")
        sampleMorph("brainrot-entry", source, expanding = true)
        rule.onNodeWithTag("shorts-fullscreen-pager").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        android.os.SystemClock.sleep(100)
        sampleMorph("brainrot-exit", source, expanding = false, part = "art")
        rule.onNodeWithTag("shorts-fullscreen-pager").assertDoesNotExist()
        rule.onNodeWithTag("now-playing-header").assertIsDisplayed()
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            if (name == "short-card-entry-middle") {
                val color = bitmap.getPixel(20, 200)
                assertTrue("A dark-mode morph must not reveal the white activity window", android.graphics.Color.red(color) < 100)
            }
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
