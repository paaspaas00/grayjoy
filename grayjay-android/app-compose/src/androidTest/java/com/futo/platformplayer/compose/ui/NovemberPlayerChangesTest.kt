package com.futo.platformplayer.compose.ui

import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import android.content.Context
import android.content.ContextWrapper
import java.util.UUID

class NovemberPlayerChangesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val video = VideoUiModel("nov-player", "A video", "Creator", "", "1:00", sourceId = "youtube")
    @Before fun prepare() = rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
    @After fun cleanup() = rule.runOnUiThread { player.release() }

    @Test fun gestureDefaultsAreOffAndTimerDefaultIsTenAndPersistsZero() {
        val base = rule.activity
        val name = "nov-settings-" + UUID.randomUUID()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val preferences = InteractionFeatures(context)
            assertFalse(preferences.fullscreenVolumeGesture)
            assertFalse(preferences.fullscreenBrightnessGesture)
            assertEquals(10, preferences.autoplayDelaySeconds)
            preferences.autoplayDelaySeconds = 0
            assertEquals(0, InteractionFeatures(context).autoplayDelaySeconds)
            preferences.fullscreenBrightnessGesture = true
            assertTrue(InteractionFeatures(context).fullscreenBrightnessGesture)
        } finally { base.deleteSharedPreferences(name) }
    }
    @Test fun timerDialogAcceptsImmediateAndRejectsOutOfRange() {
        var saved = -1
        rule.setContent { GrayjayTheme { AutoplayTimerDialog(10, { saved = it }, {}) } }
        rule.onNodeWithTag("autoplay-timer-input").performTextReplacement("121")
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.save)).assertIsNotEnabled()
        rule.onNodeWithTag("autoplay-timer-input").performTextReplacement("0")
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.save)).performClick()
        rule.runOnIdle { assertEquals(0, saved) }
    }
    @Test fun castIsInTheLeftSideOfTheNowPlayingHeader() {
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video),
            nowPlaying = NowPlayingUiState(video = video), playback = PlaybackUiState(currentVideoId = video.id),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        rule.setContent { GrayjayTheme { GrayjayApp(state.value, player, actions) } }
        val cast = rule.onNodeWithTag("chromecast-button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val header = rule.onNodeWithTag("now-playing-header").fetchSemanticsNode().boundsInRoot
        assertTrue(cast.center.x < header.center.x)
    }
    @Test fun upNextAndCommentsSwitchInBothDirections() {
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video),
            nowPlaying = NowPlayingUiState(video = video,
                comments = listOf(VideoCommentUiModel("comment", "An author", message = "A test comment", age = "now"))),
            playback = PlaybackUiState(currentVideoId = video.id),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        rule.setContent { GrayjayTheme { GrayjayApp(state.value, player, actions) } }
        rule.onNodeWithTag("video-detail-list").performScrollToNode(hasTestTag("now-playing-section-comments"))
        rule.onNodeWithTag("now-playing-section-comments").performClick()
        rule.onNodeWithTag("video-detail-list").performScrollToNode(hasText("A test comment"))
        rule.onNodeWithText("A test comment").assertIsDisplayed()
        rule.onNodeWithTag("video-detail-list").performScrollToNode(hasTestTag("now-playing-section-up-next"))
        rule.onNodeWithTag("now-playing-section-up-next").performClick()
        rule.onNodeWithText("A test comment").assertDoesNotExist()
    }

    @Test fun expandingFromMiniKeepsControlsHiddenUntilTapped() {
        var alpha by mutableFloatStateOf(0f)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            PlayerSurface(video, player, PlaybackUiState(), false, false,
                canGoPrevious = false, canGoNext = false, onTogglePlayback = {}, onSkipPrevious = {},
                onSkipNext = {}, onSeekBy = {}, onSeek = {}, onSpeedChange = {}, onQualityChange = {},
                onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {}, onRetryPlayback = {},
                onFullscreen = {}, controlsAlpha = alpha, modifier = Modifier.fillMaxSize())
        } }
        rule.waitForIdle()
        rule.runOnIdle { alpha = 1f }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        rule.onNodeWithTag("media-player").performTouchInput { click(center) }
        rule.onNodeWithTag("player-settings").assertIsDisplayed()
        val settings = rule.onNodeWithTag("player-settings").fetchSemanticsNode().boundsInRoot
        val fullscreen = rule.onNodeWithTag("player-fullscreen").fetchSemanticsNode().boundsInRoot
        assertTrue(fullscreen.left - settings.right >= 15f)
    }
    @Test fun fullscreenVerticalGesturesChangeVolumeAndRestoreWindowBrightness() {
        val audio = rule.activity.getSystemService(AudioManager::class.java)
        val previousVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val previousBrightness = rule.activity.window.attributes.screenBrightness
        var enabled by mutableStateOf(true)
        try {
            rule.runOnUiThread {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3, 0)
                rule.activity.window.attributes = rule.activity.window.attributes.apply { screenBrightness = .4f }
            }
            rule.setContent { GrayjayTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalInteractionControls provides InteractionControls(volumeGesture = enabled, brightnessGesture = enabled)) {
                    FullscreenPlayerScreen(video, player, PlaybackUiState(), false, onTogglePlayback = {},
                        onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {}, onSeek = {}, onSpeedChange = {},
                        onQualityChange = {}, onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {},
                        onRetryPlayback = {}, onExitFullscreen = {}, portraitFullscreen = true)
                }
            } }
            val initialVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            rule.onNodeWithTag("media-player").performTouchInput {
                swipe(Offset(width * .72f, height * .65f), Offset(width * .72f, height * .3f), 500)
            }
            rule.runOnIdle { assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC) > initialVolume) }
            rule.onNodeWithTag("media-player").performTouchInput {
                swipe(Offset(width * .28f, height * .65f), Offset(width * .28f, height * .3f), 500)
            }
            rule.runOnIdle { assertTrue(rule.activity.window.attributes.screenBrightness > .4f); enabled = false }
            rule.runOnIdle { assertEquals(.4f, rule.activity.window.attributes.screenBrightness, .001f) }
        } finally { rule.runOnUiThread {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            rule.activity.window.attributes = rule.activity.window.attributes.apply { screenBrightness = previousBrightness }
        } }
    }
    @Test fun upwardChannelDragFillsTheSpaceBelowTheSheet() {
        lateinit var controller: CreatorSheetController
        var sheetColor = Color.Unspecified
        val channel = ChannelUiModel("nov-channel", "A channel", "youtube", "YouTube", 0, "", "Description")
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
            CreatorSheetTransitionHost("fixture", {}, channel, false) {
                controller = checkNotNull(LocalCreatorSheet.current)
                Surface(Modifier.fillMaxSize(), color = Color.Magenta) {}
            }
        } }
        rule.runOnIdle { controller.show(channel, false, {}, {}) }
        val sheet = rule.onNodeWithTag("creator-transition-sheet")
        sheet.performTouchInput { down(center); moveBy(Offset(0f, -80f)) }
        val bitmap = rule.onRoot().captureToImage()
        val image = bitmap.toPixelMap()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "nov-channel-stretched.png").outputStream().use {
            bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val pixel = image[image.width / 2, image.height - 80]
        assertEquals(sheetColor.red, pixel.red, .025f)
        assertEquals(sheetColor.green, pixel.green, .025f)
        assertEquals(sheetColor.blue, pixel.blue, .025f)
        sheet.performTouchInput { cancel() }
    }
}
