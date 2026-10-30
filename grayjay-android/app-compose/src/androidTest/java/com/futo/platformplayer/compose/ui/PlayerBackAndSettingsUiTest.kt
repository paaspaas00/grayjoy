package com.futo.platformplayer.compose.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PlayerBackAndSettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var playerBackground = Color.Unspecified
    @Before fun setup() = rule.runOnUiThread {
        orientation = rule.activity.requestedOrientation
        rule.activity.enableEdgeToEdge()
        player = ExoPlayer.Builder(rule.activity.applicationContext).build()
    }
    @After fun cleanup() = rule.runOnUiThread { player.release(); rule.activity.requestedOrientation = orientation }
    private fun startPlayer(music: Boolean = false, playlist: Boolean = false) {
        val video = VideoUiModel("back-video", "A playing song", "Artist", "", "3:00",
            sourceId = if (music) "spotify" else "youtube",
            thumbnailUrl = "android.resource://${InstrumentationRegistry.getInstrumentation().context.packageName}/drawable/audit_earth")
        val origin = PlaylistUiModel("back-list", "The source playlist", "", listOf(video.id))
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video), libraryVideos = listOf(video),
            playlists = if (playlist) listOf(origin) else emptyList(), playbackPlaylist = origin.takeIf { playlist },
            sources = listOf(SourceUiModel(video.sourceId, video.sourceId, "", 0L, true)),
            home = HomeUiState(videos = listOf(video)), nowPlaying = NowPlayingUiState(video = video),
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id), durationMs = 180_000),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        val actions = GrayjayAppActions().apply { onExternalNavigationHandled = { state = state.copy(externalNavigation = null) } }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true, musicProfile = music) {
            playerBackground = MaterialTheme.colorScheme.surface
            CompositionLocalProvider(LocalVideoFrameReadiness provides { false }) { GrayjayApp(state, player, actions) }
        } }
        rule.onNodeWithTag("now-playing-header").assertIsDisplayed()
    }
    @Test fun androidBackUsesPageDepartureAndRaisesAnIndependentMiniPlayer() {
        startPlayer()
        val surface = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(80)
        val middle = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Back must not shrink the video into the mini slot", middle.width > surface.width * .8f)
        assertTrue("Back must not drag the video down to navigation", middle.top < surface.top + 180f)
        rule.onNodeWithTag("back-miniplayer-slot", useUnmergedTree = true).assertExists()
        capture("nowplaying-button-back-middle")
        rule.mainClock.advanceTimeBy(500); rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("now-playing-header").assertDoesNotExist()
        rule.onNodeWithTag("back-miniplayer-slot", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
    }
    @Test fun cancellingPredictiveBackRestoresThePlayerAndHidesTheMiniPreview() {
        startPlayer()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT)) }
        rule.mainClock.advanceTimeBy(16)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 400f, .45f, BackEventCompat.EDGE_LEFT)) }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("back-miniplayer-slot", useUnmergedTree = true).assertExists()
        val slot = rule.onNodeWithTag("inline-player-slot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val viewport = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val rendered = rule.onNodeWithTag("media-player", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.intersect(viewport)
        assertEquals("The native video must follow the page exactly once", slot.left, rendered.left, 3f)
        assertEquals(slot.top, rendered.top, 3f)
        assertEquals(slot.width, rendered.width, 3f)
        assertEquals(slot.height, rendered.height, 3f)
        val mini = rule.onNodeWithTag("back-miniplayer-slot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val sample = pixels[(mini.width * .6f).toInt(), (mini.top + mini.height * .9f - viewport.top).toInt()]
        assertTrue("The outgoing Now Playing page must cover the mini player",
            kotlin.math.abs(sample.red - playerBackground.red) < .04f &&
                kotlin.math.abs(sample.green - playerBackground.green) < .04f &&
                kotlin.math.abs(sample.blue - playerBackground.blue) < .04f)
        capture("nowplaying-predictive-back-middle")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        rule.mainClock.advanceTimeBy(400); rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("now-playing-header").assertIsDisplayed()
        rule.onNodeWithTag("back-miniplayer-slot", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("mini-player").assertDoesNotExist()
    }
    @Test fun committedPredictiveBackReturnsToThePlaybackPlaylist() {
        startPlayer(playlist = true)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(1000f, 400f, 0f, BackEventCompat.EDGE_RIGHT)) }
        rule.mainClock.advanceTimeBy(16)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(800f, 400f, .6f, BackEventCompat.EDGE_RIGHT)) }
        rule.mainClock.advanceTimeBy(32)
        capture("nowplaying-playlist-back-middle")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(500); rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("playlist-detail-back-list").assertIsDisplayed()
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        rule.onNodeWithTag("now-playing-header").assertDoesNotExist()
    }
    @Test fun musicFullscreenUsesTheAnchoredMenuWithoutVideoOnlyOptions() {
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5000) { rule.activity.resources.configuration.screenWidthDp > rule.activity.resources.configuration.screenHeightDp }
        val video = VideoUiModel("music", "Song", "Artist", "", "3:00", sourceId = "spotify")
        val state = PlaybackUiState(currentVideoId = video.id, durationMs = 180_000)
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true, musicProfile = true) {
            CompositionLocalProvider(LocalMusicPresentation provides true) {
                FullscreenPlayerScreen(video, player, state, false, onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {},
                    onSeekBy = {}, onSeek = {}, onSpeedChange = {}, onQualityChange = {}, onCaptionsEnabledChange = {},
                    onSubtitleLanguageChange = {}, onRetryPlayback = {}, onExitFullscreen = {}, portraitFullscreen = false,
                    musicContent = { openSettings ->
                        MusicNowPlaying(video, player, state, NowPlayingUiState(video = video), emptyList(),
                            onToggle = {}, onPrevious = {}, onNext = {}, onSeek = {}, onWatchLater = {}, onDownload = {},
                            onAddToPlaylist = {}, onRetry = {}, onPlay = {}, onMore = {}, onSpeed = {}, onArtworkBounds = {},
                            fullscreen = true, onFullscreen = {}, onOpenSettings = openSettings)
                    })
            }
        } }
        val anchor = rule.onNodeWithTag("music-settings").performScrollTo().fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("music-settings").performScrollTo().performClick()
        val menu = rule.onNodeWithTag("fullscreen-settings-menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The music menu must cover its anchor", menu.contains(anchor.center))
        rule.onNodeWithTag("player-settings-quality").assertDoesNotExist()
        rule.onNodeWithTag("player-settings-subtitles").assertDoesNotExist()
        rule.onNodeWithTag("player-settings-audio-language").assertDoesNotExist()
        rule.onNodeWithTag("player-settings-lock").assertDoesNotExist()
        capture("spotify-fullscreen-settings-main")
        rule.onNodeWithTag("player-settings-speed").performClick()
        rule.onNodeWithTag("player-settings-back").assertIsDisplayed()
        capture("spotify-fullscreen-settings-speed")
        rule.onNodeWithTag("player-settings-back").performClick()
        rule.onNodeWithTag("fullscreen-settings-outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, centerY)) }
        rule.onNodeWithTag("fullscreen-settings-menu").assertDoesNotExist()
    }
    @Test fun setupKeepsOneHandedLayoutAndExplainsNormalizationWithoutLoudnessUnits() {
        var state by mutableStateOf(GrayjayUiState())
        var page by mutableIntStateOf(guidedSetupSteps(false, false).indexOf(SetupStep.PlayerLayout))
        val actions = GrayjayAppActions().apply {
            onOneHandedPlayerChange = { state = state.copy(oneHandedPlayerEnabled = it) }
            onAudioNormalizationChange = { state = state.copy(audioNormalizationEnabled = it) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GuidedSetupDialog(state, actions, page, { page = it }, {}, {}) } }
        rule.onNodeWithText(rule.activity.getString(R.string.experimental_features)).assertDoesNotExist()
        rule.onNodeWithTag("setup-player-one-handed").performClick()
        rule.runOnIdle { assertTrue(state.oneHandedPlayerEnabled) }
        rule.onNodeWithTag("setup-next").performClick()
        val body = rule.activity.getString(R.string.setup_audio_normalization_body)
        assertFalse(body.contains("LUFS", true))
        rule.onNodeWithText(body).assertIsDisplayed()
        rule.onAllNodesWithText(rule.activity.getString(R.string.audio_normalization)).filter(hasClickAction()).onFirst().performClick()
        rule.runOnIdle { assertTrue(state.audioNormalizationEnabled) }
        capture("setup-audio-normalization")
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_song_recognition_body)).assertIsDisplayed()
        capture("setup-shazam-internet")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
