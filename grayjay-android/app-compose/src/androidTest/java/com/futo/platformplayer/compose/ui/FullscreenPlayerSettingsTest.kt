package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.FullscreenPlayerScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FullscreenPlayerSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    @Test fun anchoredMenuNavigatesAndDismissesWithoutLeavingFullscreen() {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player = ExoPlayer.Builder(rule.activity).build()
        }
        var exited = false
        var toggled = false
        var quality: Int? = null
        var state by mutableStateOf(PlaybackUiState(durationMs = 60_000L,
            currentVideoHeight = 1080, availableVideoQualities = listOf(1080, 720, 480, 360, 240, 144)))
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            FullscreenPlayerScreen(
                VideoUiModel("settings-fixture", "A video title", "Channel", "", "1:00", sourceId = "youtube"),
                player, state, false,
                onTogglePlayback = { toggled = true }, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {}, onSeek = {},
                onSpeedChange = {}, onQualityChange = { quality = it; state = state.copy(selectedVideoQuality = it) },
                onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {}, onRetryPlayback = {},
                onExitFullscreen = { exited = true }, portraitFullscreen = true,
            )
        } }
        val anchor = rule.onNodeWithTag("player-settings").fetchSemanticsNode().boundsInRoot
        val lock = rule.onNodeWithTag("player-lock").fetchSemanticsNode().boundsInRoot
        val exit = rule.onNodeWithTag("player-fullscreen").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("player-settings").performTouchInput { click() }
        val menu = rule.onNodeWithTag("fullscreen-settings-menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("fullscreen-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait fullscreen buttons need a vertical gap", exit.top - anchor.bottom >= 12f)
        assertTrue("Menu must cover the settings and lock button row", menu.top <= anchor.top && menu.bottom >= anchor.bottom)
        assertTrue(menu.left <= lock.left && menu.right >= anchor.right)
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        rule.onNodeWithTag("player-lock").assertDoesNotExist()
        rule.onNodeWithTag("player-settings-lock").assertDoesNotExist()
        assertTrue(menu.left >= viewport.left && menu.right <= viewport.right && menu.bottom <= viewport.bottom)
        capture("fullscreen-settings-main")
        rule.onNodeWithTag("player-settings-quality").performTouchInput { click() }
        rule.onNodeWithTag("player-settings-back").assertIsDisplayed()
        rule.onNodeWithText("720p").assertIsDisplayed()
        capture("fullscreen-settings-quality")
        // The label and the empty far end of the header are both Back targets.
        rule.onNodeWithTag("player-settings-back").performTouchInput { click(Offset(width - 8f, centerY)) }
        rule.onNodeWithTag("player-settings-quality").assertIsDisplayed()
        rule.onNodeWithTag("player-settings-quality").performClick()
        rule.onNodeWithText("720p").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("player-settings-quality").assertIsDisplayed()
        assertFalse(exited)
        rule.onNodeWithTag("player-settings-quality").performTouchInput { click() }
        rule.onNodeWithText("720p").performTouchInput { click() }
        rule.onNodeWithTag("fullscreen-settings-menu").assertDoesNotExist()
        assertEquals(720, quality)
        assertFalse(exited)
        rule.onNodeWithTag("player-settings").performTouchInput { click() }
        rule.onNodeWithTag("fullscreen-settings-outside").performTouchInput { click(Offset(4f, centerY)) }
        rule.onNodeWithTag("fullscreen-settings-menu").assertDoesNotExist()
        assertFalse(toggled)
        rule.onNodeWithTag("fullscreen-player").assertIsDisplayed()
    }

    @Test fun hiddenControlsRevealImmediatelyAndToolbarLockCanBeUndone() {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player = ExoPlayer.Builder(rule.activity).build()
        }
        var sought = 0L
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            FullscreenPlayerScreen(VideoUiModel("tap-fixture", "Video", "Channel", "", "1:00"),
                player, PlaybackUiState(durationMs = 60_000L, isPlaying = true), false,
                onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = { sought += it }, onSeek = {},
                onSpeedChange = {}, onQualityChange = {}, onCaptionsEnabledChange = {},
                onSubtitleLanguageChange = {}, onRetryPlayback = {}, onExitFullscreen = {}, portraitFullscreen = true)
        } }
        rule.mainClock.advanceTimeBy(4_000)
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("media-player").performTouchInput { click(Offset(width * .25f, height * .45f)) }
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithTag("player-settings").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(400) // A separate tap, not the second half of a double tap.
        rule.onNodeWithTag("media-player").performTouchInput { click(Offset(width * .25f, height * .45f)) }
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("media-player").performTouchInput { click(Offset(width * .25f, height * .45f)) }
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithTag("player-settings").assertIsDisplayed()
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("player-lock").performTouchInput { click() }
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        val unlock = rule.onNodeWithTag("player-unlock").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("fullscreen-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Unlock belongs to the lower left area", unlock.right < viewport.center.x && unlock.top > viewport.center.y)
        assertTrue("Unlock must leave space above the bottom navigation area", unlock.bottom < viewport.bottom - 24f)
        capture("fullscreen-unlock-lower-left")
        rule.onNodeWithTag("player-unlock").assertIsDisplayed().performTouchInput { click() }
        rule.onNodeWithTag("player-settings").assertIsDisplayed()
        rule.onNodeWithTag("media-player").performTouchInput { doubleClick(Offset(width * .8f, height * .45f)) }
        rule.runOnIdle { assertEquals(10_000L, sought) }
    }

    @Test fun playPauseMorphIsReversibleAndHoldIndicatorStopsOnRelease() {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player = ExoPlayer.Builder(rule.activity).build()
        }
        var playback by mutableStateOf(PlaybackUiState(durationMs = 60_000L))
        var toggles = 0
        var holdStarts = 0
        var holdEnds = 0
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            FullscreenPlayerScreen(VideoUiModel("morph-fixture", "Player controls", "Channel", "", "1:00"),
                player, playback, false,
                onTogglePlayback = { toggles++; playback = playback.copy(isPlaying = !playback.isPlaying) },
                onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {}, onSeek = {}, onSpeedChange = {},
                holdToSpeedEnabled = true, onSpeedHoldStart = { holdStarts++ }, onSpeedHoldEnd = { holdEnds++ },
                onQualityChange = {}, onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {},
                onRetryPlayback = {}, onExitFullscreen = {}, portraitFullscreen = true)
        } }
        rule.onNodeWithTag("player-play-pause").assertWidthIsEqualTo(96.dp).assertHeightIsEqualTo(96.dp)
        val icon = rule.onNodeWithTag("player-play-pause-icon", useUnmergedTree = true)
        icon.assertWidthIsEqualTo(52.dp).assertHeightIsEqualTo(52.dp)
        rule.mainClock.autoAdvance = false
        val play = icon.captureToImage()
        rule.onNodeWithTag("player-play-pause").performClick()
        rule.mainClock.advanceTimeBy(80)
        val towardsPause = icon.captureToImage()
        capture("play-pause-morph-middle")
        rule.mainClock.advanceTimeBy(200)
        val pause = icon.captureToImage()
        assertTrue("The transition must draw an intermediate shape", imagesDiffer(play, towardsPause) && imagesDiffer(towardsPause, pause))
        val pausePixels = pause.toPixelMap()
        assertTrue("Pause must have a visible central gap", pausePixels[pause.width / 2, pause.height / 2].red < .3f)
        capture("play-pause-morph-pause")
        rule.onNodeWithTag("player-play-pause").performClick()
        rule.mainClock.advanceTimeBy(80)
        val towardsPlay = icon.captureToImage()
        rule.mainClock.advanceTimeBy(200)
        assertTrue(imagesDiffer(towardsPlay, pause) && imagesDiffer(towardsPlay, play))
        assertFalse("The reverse morph must restore the play triangle", imagesDiffer(play, icon.captureToImage()))
        assertEquals(2, toggles)
        capture("play-pause-morph-play")
        // Reverse an in-flight animation too; no jump or stale pause icon should remain.
        rule.onNodeWithTag("player-play-pause").performClick()
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithTag("player-play-pause").performClick()
        rule.mainClock.advanceTimeBy(240)
        assertFalse(imagesDiffer(play, icon.captureToImage()))
        rule.onNodeWithTag("media-player").performTouchInput { down(Offset(width * .2f, height * .6f)) }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("hold-speed-indicator").assertIsDisplayed()
        rule.onNodeWithTag("player-settings").assertDoesNotExist()
        assertEquals(1, holdStarts)
        capture("player-larger-hold-speed")
        rule.onNodeWithTag("media-player").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("hold-speed-indicator").assertDoesNotExist()
        rule.onNodeWithTag("player-settings").assertIsDisplayed()
        assertEquals(1, holdEnds)
        rule.mainClock.autoAdvance = true
    }

    private fun imagesDiffer(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return true
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        // Compare the white glyph, not the independently animated Surface press ripple.
        var changed = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            val firstIsGlyph = a[x, y].red > .9f && a[x, y].green > .9f && a[x, y].blue > .9f
            val secondIsGlyph = b[x, y].red > .9f && b[x, y].green > .9f && b[x, y].blue > .9f
            if (firstIsGlyph != secondIsGlyph) changed++
        }
        return changed > first.width * first.height / 100
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
