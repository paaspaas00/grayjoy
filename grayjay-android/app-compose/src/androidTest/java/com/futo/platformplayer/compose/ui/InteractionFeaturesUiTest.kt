package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.FullscreenPlayerScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class InteractionFeaturesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @Before fun initialize() { rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() } }
    @After fun cleanup() { rule.runOnUiThread { player.release() } }
    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(60) // Let SurfaceFlinger present the frame, also with the test clock frozen.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun libraryLongPressShowsIconsAndNavigatesWithoutBreakingOrdinaryTaps() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
        rule.onNodeWithTag("nav-library").performTouchInput { longClick() }
        rule.onNodeWithTag("library-shortcuts").assertIsDisplayed()
        capture("interaction-library-shortcuts")
        rule.onNodeWithTag("library-shortcut-downloads").performClick()
        rule.onNodeWithTag("library-filter-downloads").assertIsSelected()
        rule.onNodeWithTag("nav-search").performTouchInput { click() }
        rule.onNodeWithTag("search-field").assertIsDisplayed()
    }
    @Test fun spotifySearchUsesArtistsAndTracksAndGroupingLivesInDialog() {
        var state by mutableStateOf(GrayjayUiState(sources = listOf(SourceUiModel("spotify", "Spotify", "", 0, true))))
        val actions = GrayjayAppActions().apply { onGroupHistoryByPlaylistChange = { state = state.copy(groupHistoryByPlaylist = it) } }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("search-filter-videos").assertTextContains(rule.activity.getString(R.string.music_tracks))
        rule.onNodeWithTag("search-filter-creators").assertTextContains(rule.activity.getString(R.string.music_artists))
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.group_history_by_playlist)).assertDoesNotExist()
        val header = rule.onNodeWithTag("library-section-header-history").fetchSemanticsNode().boundsInRoot
        val grouping = rule.onNodeWithTag("history-group-settings").fetchSemanticsNode().boundsInRoot
        val endInset = header.right - grouping.right
        assertTrue("Grouping belongs at the right edge, allowing the Material button's internal inset",
            grouping.center.x > header.center.x && endInset >= 0f && endInset <= 8f * rule.activity.resources.displayMetrics.density)
        rule.onNodeWithTag("history-group-settings").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.history_group_explanation)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.group_history_by_playlist)).performClick()
        rule.runOnIdle { assertTrue(state.groupHistoryByPlaylist) }
        capture("interaction-history-group-dialog")
    }
    @Test fun portraitShortsActionsAreVerticalAndMoveToLeftWithoutAutoShowingInBrainrot() {
        var left by mutableStateOf(false)
        var brainrot by mutableStateOf(false)
        var video by mutableStateOf(VideoUiModel("portrait-fixture", "Vertical video", "Channel", "", "1:00"))
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            CompositionLocalProvider(LocalInteractionControls provides InteractionControls(leftHanded = left), LocalBrainrotPlayer provides brainrot) {
                FullscreenPlayerScreen(video, player, PlaybackUiState(durationMs = 60000), false,
                    onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {}, onSeek = {}, onSpeedChange = {},
                    onQualityChange = {}, onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {},
                    onRetryPlayback = {}, onExitFullscreen = {}, portraitFullscreen = true)
            }
        } }
        fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val lock = bounds("player-lock"); val settings = bounds("player-settings"); val fullscreen = bounds("player-fullscreen")
        assertTrue(lock.center.y < settings.center.y && settings.center.y < fullscreen.center.y)
        assertEquals(settings.center.x, fullscreen.center.x, 2f)
        val width = rule.onNodeWithTag("fullscreen-player").fetchSemanticsNode().boundsInRoot.width
        assertTrue(settings.center.x > width / 2)
        capture("interaction-short-controls-right")
        rule.runOnIdle { left = true }
        assertTrue(bounds("player-settings").center.x < width / 2)
        capture("interaction-short-controls-left")
        rule.runOnIdle { brainrot = true; video = video.copy(id = "next-short") }
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        rule.onNodeWithTag("media-player").performTouchInput { click() }
        rule.onNodeWithTag("player-settings").assertIsDisplayed()
    }
    @Test fun longSearchOpensRecognitionAndClosingReleasesTheUi() {
        val prefs = InteractionFeatures(rule.activity)
        val previous = prefs.recognitionEnabled
        prefs.recognitionEnabled = true
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
            rule.onNodeWithTag("nav-search").performTouchInput { longClick() }
            rule.onNodeWithTag("song-recognition").assertIsDisplayed()
            rule.onAllNodes(isDialog()).assertCountEquals(0)
            rule.onNodeWithTag("recognition-waveform").assertIsDisplayed()
            val nav = rule.onNodeWithTag("bottom-navigation-bar").fetchSemanticsNode().boundsInRoot
            val panel = rule.onNodeWithTag("song-recognition").fetchSemanticsNode().boundsInRoot
            assertEquals("The panel must stop behind the foreground navigation bar", nav.top, panel.bottom, 2f)
            capture("interaction-song-recognition")
            rule.onNodeWithText(rule.activity.getString(R.string.close)).performClick()
            rule.onNodeWithTag("song-recognition").assertDoesNotExist()
            rule.onNodeWithTag("nav-home").assertIsSelected()
            rule.onNodeWithTag("nav-library").performClick()
            rule.onNodeWithTag("library-filter-history").assertIsDisplayed()
        } finally { prefs.recognitionEnabled = previous }
    }
    @Test fun navigationStaysClickableAboveTheRecognitionPanel() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
        rule.onNodeWithTag("nav-search").performTouchInput { longClick() }
        rule.onNodeWithTag("song-recognition").assertIsDisplayed()
        rule.onNodeWithTag("nav-library").performTouchInput { click() }
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.onNodeWithTag("library-filter-history").assertIsDisplayed()
    }
    @Test fun quickMenuAndRecognitionSlideUpFromBehindNavigation() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("nav-library").performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(112)
        val movingMenuTop = rule.onNodeWithTag("library-shortcuts").fetchSemanticsNode().boundsInRoot.top
        capture("recognition-menu-entering")
        rule.mainClock.advanceTimeBy(400)
        val restingMenuTop = rule.onNodeWithTag("library-shortcuts").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The menu must travel upwards, not expand at its destination", movingMenuTop > restingMenuTop + 20)
        capture("recognition-menu-resting")
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("library-shortcuts-outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("nav-search").performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(112)
        val movingPanelTop = rule.onNodeWithTag("song-recognition").fetchSemanticsNode().boundsInRoot.top
        capture("recognition-panel-entering")
        rule.mainClock.advanceTimeBy(400)
        val restingPanelTop = rule.onNodeWithTag("song-recognition").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The panel must travel upwards from navigation", movingPanelTop > restingPanelTop + 20)
        capture("recognition-panel-resting")
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText(rule.activity.getString(R.string.close)).performClick()
    }
    @Test fun waveformReferenceStyleIsReadableInBothThemes() {
        var dark by mutableStateOf(true)
        val level = mutableStateOf(com.futo.platformplayer.compose.recognition.RecognitionAudioFrame.Silence)
        rule.mainClock.autoAdvance = false
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = dark) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(rule.activity.getString(R.string.recognition_listening))
                    com.futo.platformplayer.compose.recognition.RecognitionWaveform(level, active = true)
                }
            }
        } }
        rule.onNodeWithTag("recognition-waveform").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        val leftPacket = floatArrayOf(0f, .16f, .65f, .95f, 1f, .72f, .55f, .4f, .25f, .12f, .02f, 0f, 0f, 0f, 0f)
        rule.runOnIdle { level.value = com.futo.platformplayer.compose.recognition.RecognitionAudioFrame(.9f, leftPacket) }
        rule.mainClock.advanceTimeBy(600)
        capture("recognition-waveform-left-packet")
        val broad = floatArrayOf(0f, .12f, .62f, .8f, 1f, .73f, .8f, .88f, .96f, .7f, .94f, .55f, .7f, .25f, 0f)
        rule.runOnIdle { level.value = com.futo.platformplayer.compose.recognition.RecognitionAudioFrame(.9f, broad) }
        rule.mainClock.advanceTimeBy(448)
        capture("recognition-waveform-dark")
        rule.runOnIdle { dark = false }
        rule.mainClock.advanceTimeBy(32)
        capture("recognition-waveform-light")
        rule.runOnIdle { level.value = com.futo.platformplayer.compose.recognition.RecognitionAudioFrame.Silence }
        rule.mainClock.advanceTimeBy(1400)
        capture("recognition-waveform-idle")
        rule.mainClock.autoAdvance = true
    }
    @Test fun preferenceSearchHighlightsTheMatchingWords() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("settings-search").performTextInput("audio")
        rule.waitForIdle()
        val matches = rule.onAllNodes(hasText("audio", substring = true, ignoreCase = true)).fetchSemanticsNodes()
        assertTrue(matches.any { node -> node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .any { it.spanStyles.isNotEmpty() } })
        capture("interaction-settings-highlight")
    }
}
