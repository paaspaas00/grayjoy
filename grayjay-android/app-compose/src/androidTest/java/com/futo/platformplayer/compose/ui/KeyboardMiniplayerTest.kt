package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real system IME and isolated fixture state; the user's library is never read or changed. */
class KeyboardMiniplayerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private lateinit var composeView: android.view.View
    private val current = VideoUiModel("keyboard-video", "Playback above the keyboard", "Test channel", "", "3:00", lastWatchedAt = 1L)
    private var state by mutableStateOf(GrayjayUiState(
        videos = listOf(current), libraryVideos = listOf(current),
        playback = PlaybackUiState(currentVideoId = current.id, queueVideoIds = listOf(current.id)),
        nowPlaying = NowPlayingUiState(video = current),
    ))
    private var closed = false

    @After fun clean() {
        if (::player.isInitialized) rule.runOnUiThread { player.release() }
    }

    private fun start() {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.setSoftInputMode(if (android.os.Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            player = ExoPlayer.Builder(rule.activity).build()
        }
        val actions = GrayjayAppActions().apply {
            onClosePlayback = {
                closed = true
                state = state.copy(playback = PlaybackUiState(), nowPlaying = NowPlayingUiState())
            }
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
        }
        rule.setContent {
            composeView = androidx.compose.ui.platform.LocalView.current
            GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) }
        }
    }

    @Test fun playerFollowsKeyboardInSearchHistoryAndChannel() {
        start()
        rule.onNodeWithTag("nav-search").performClick()
        checkKeyboard("search-field", "keyboard-search")
        rule.onNodeWithTag("nav-library").performClick()
        checkKeyboard("history-search", "keyboard-history")

        val channel = ChannelUiModel("keyboard-channel", "Test channel", "youtube", "YouTube", 0, "", "Description")
        rule.runOnIdle {
            state = state.copy(channels = listOf(channel),
                channelDetail = ChannelDetailUiState(channelId = channel.id, channel = channel, videos = listOf(current), isLoaded = true, supportsShorts = true),
                externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Channel, channel.id))
        }
        rule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasTestTag("channel-video-search"))
        checkKeyboard("channel-video-search", "keyboard-channel")
        rule.onNodeWithTag("browse-top-bar").assertDoesNotExist()
        rule.onNodeWithTag("channel-tab-videos").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithTag("browse-top-bar").assertIsDisplayed()
    }

    private fun checkKeyboard(field: String, screenshot: String) {
        rule.onNodeWithTag(field).performClick()
        rule.waitUntil(5_000) { imeBottom() > 0 }
        rule.waitForIdle()
        android.os.SystemClock.sleep(350) // The system IME animation runs outside the Compose clock.
        val mini = rule.onNodeWithTag("mini-player").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val screenOffset = IntArray(2)
        var keyboardTop = 0
        var statusTop = 0
        rule.runOnUiThread {
            composeView.getLocationOnScreen(screenOffset)
            keyboardTop = rule.activity.window.decorView.height - imeBottom()
            statusTop = ViewCompat.getRootWindowInsets(composeView)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        assertEquals("Miniplayer must touch the top edge of the keyboard on screen", keyboardTop.toFloat(), mini.bottom + screenOffset[1], 3f)
        val header = if (field == "channel-video-search") "channel-tab-videos" else field
        assertTrue("Header must remain below the status bar on screen",
            rule.onNodeWithTag(header).fetchSemanticsNode().boundsInRoot.top + screenOffset[1] >= statusTop)
        if (field == "channel-video-search") {
            rule.onNodeWithTag("channel-follow").assertDoesNotExist()
            val tabsTop = rule.onNodeWithTag(header).fetchSemanticsNode().boundsInRoot.top + screenOffset[1]
            assertTrue("Search controls must stay pinned even for a short result list", tabsTop - statusTop < 96f)
        }
        rule.onNodeWithTag("mini-player-close").assertIsDisplayed()
        capture(screenshot)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { imeBottom() == 0 }
        rule.waitForIdle()
        android.os.SystemClock.sleep(350)
        val nav = rule.onNodeWithTag("nav-search").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val restored = rule.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Miniplayer must stay above the navigation controls", restored.bottom <= nav.top + 3f)
    }

    @Test fun closeMovesPlayerDownBeforeRemovingItAndKeepsNavigationWorking() {
        start()
        rule.onNodeWithTag("nav-library").performClick()
        val before = rule.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        val screenshotBefore = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("mini-player-close").performClick()
        rule.mainClock.advanceTimeBy(96)
        val during = rule.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Dismiss animation moves down", during.top > before.top)
        assertFalse("Playback closes after the animation", closed)
        val screenshotDuring = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        try {
            // The thumbnail is drawn in a separate layer; its fading edge must never cover
            // even the top strip of the navbar. A geometry-only assertion cannot detect that.
            val navTop = before.bottom.toInt() + 8
            for (y in navTop until navTop + 16) for (x in 8 until 100) {
                assertEquals("Dismissed video painted over the navbar at ($x, $y)",
                    screenshotBefore.getPixel(x, y), screenshotDuring.getPixel(x, y))
            }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            File(app.getExternalFilesDir(null), "miniplayer-dismiss-behind-navbar.png").outputStream().use {
                screenshotDuring.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshotBefore.recycle()
            screenshotDuring.recycle()
        }
        rule.mainClock.advanceTimeBy(240)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(closed)
        rule.onNodeWithTag("mini-player").assertDoesNotExist()
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("search-field").assertIsDisplayed()
    }

    @Test fun searchHeaderDoesNotBounceWhileSwitchingNavbarTabs() {
        start()
        rule.onNodeWithTag("nav-library").performClick()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("nav-search").performClick()
        rule.mainClock.advanceTimeByFrame()
        val top = rule.onNodeWithTag("search-field").fetchSemanticsNode().boundsInRoot.top
        repeat(14) {
            rule.mainClock.advanceTimeByFrame()
            assertEquals(top, rule.onNodeWithTag("search-field").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        rule.mainClock.autoAdvance = true
    }

    private fun imeBottom(): Int = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
        ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
