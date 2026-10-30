package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MiniplayerDismissUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var previousLeft = false
    @Before fun setup() = rule.runOnUiThread {
        rule.activity.enableEdgeToEdge()
        previousLeft = InteractionFeatures(rule.activity).leftHanded
        InteractionFeatures(rule.activity).leftHanded = false
        player = ExoPlayer.Builder(rule.activity).build()
    }
    @After fun cleanup() = rule.runOnUiThread { player.release(); InteractionFeatures(rule.activity).leftHanded = previousLeft }

    @Test fun nativeVideoViewDismissIsClippedAboveNavigationAndClosesOnce() = checkDismiss(false)
    @Test fun leftHandedNativeVideoViewUsesTheSameDismissTimeline() = checkDismiss(true)

    @Test fun aNewVideoCancelsThePreviousDismissWithoutClosingTheNewPlayback() {
        val first = VideoUiModel("first-dismiss", "First", "Channel", "", "", playbackAudioOnly = true)
        val next = first.copy(id = "next-dismiss", title = "Next")
        var state by mutableStateOf(GrayjayUiState(videos = listOf(first, next), nowPlaying = NowPlayingUiState(video = first),
            playback = PlaybackUiState(currentVideoId = first.id)))
        var closes = 0
        val actions = GrayjayAppActions().apply { onClosePlayback = { closes++ } }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
        rule.mainClock.autoAdvance = false
        val close = rule.onNodeWithTag("mini-player-close").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnUiThread { close() }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread { state = state.copy(nowPlaying = NowPlayingUiState(video = next), playback = PlaybackUiState(currentVideoId = next.id)) }
        rule.mainClock.advanceTimeBy(400); rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("mini-player-toggle").assertIsEnabled()
        rule.onNodeWithText("Next").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, closes) }
    }

    @Test fun invisibleClosingChromeCannotReactivateWhileTheBackendFinishesStopping() {
        val video = VideoUiModel("pending-stop", "Pending stop", "Channel", "", "", playbackAudioOnly = true)
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video), nowPlaying = NowPlayingUiState(video = video),
            playback = PlaybackUiState(currentVideoId = video.id)))
        var closes = 0
        val actions = GrayjayAppActions().apply { onClosePlayback = { closes++ } }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("mini-player-close").performClick()
        rule.onNodeWithTag("mini-player-toggle").assertIsNotEnabled()
        rule.onNodeWithTag("mini-player-close").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, closes); state = state.copy(nowPlaying = NowPlayingUiState(), playback = PlaybackUiState()) }
        rule.onNodeWithTag("mini-player").assertDoesNotExist()
    }

    private fun checkDismiss(leftHanded: Boolean) {
        val video = VideoUiModel("dismiss-video", "Dismiss test", "Test channel", "", "0:20")
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video),
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id), durationMs = 20000, isPlaying = true),
            nowPlaying = NowPlayingUiState(video = video)))
        var closes = 0
        rule.runOnUiThread {
            InteractionFeatures(rule.activity).leftHanded = leftHanded
        }
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onClosePlayback = {
                closes++; player.stop(); player.clearMediaItems()
                state = state.copy(playback = PlaybackUiState(), nowPlaying = NowPlayingUiState())
            }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(com.futo.platformplayer.compose.ui.screens.LocalVideoFrameReadiness provides { true }) {
                GrayjayApp(state, player, actions)
            }
        } }
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        // A known color on the real native PlayerView isolates composition/alpha/clipping
        // from codec availability. It is deliberately not a video-decoder correctness test.
        var nativeView: androidx.media3.ui.PlayerView? = null
        rule.runOnUiThread {
            fun find(view: android.view.View) {
                if (view is androidx.media3.ui.PlayerView && view.player === player) nativeView = view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))
            }
            find(rule.activity.window.decorView)
            checkNotNull(nativeView).foreground = android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(64, 232, 96))
        }
        val nav = rule.onNodeWithTag("nav-library").fetchSemanticsNode().boundsInRoot
        val art = rule.onNodeWithTag("mini-player-artwork", true).fetchSemanticsNode().boundsInRoot
        val media = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        val x = (media.left + media.width * .43f).toInt()
        val y = (media.top + media.height * .47f).toInt()
        val before = screenshot("${leftHanded}-dismiss-000")
        val initialColor = before.getPixel(x, y)
        assertTrue("Fixture must display the native view's green test surface",
            android.graphics.Color.green(initialColor) > android.graphics.Color.red(initialColor) + 50)
        rule.mainClock.autoAdvance = false
        val close = rule.onNodeWithTag("mini-player-close").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnUiThread { close() }
        var elapsed = 0L
        val maxShifts = mutableListOf<Float>()
        for (frame in listOf(32L, 64L, 96L, 128L, 160L, 192L, 240L, 320L)) {
            rule.mainClock.advanceTimeBy(frame - elapsed); elapsed = frame; rule.waitForIdle()
            val shot = screenshot("${leftHanded}-dismiss-${frame}")
            if (frame <= 160) rule.runOnUiThread {
                assertTrue("Native video alpha must follow dismiss", checkNotNull(nativeView).alpha in 0f..1f)
            }
            for (sampleY in art.top.toInt() + 3 until art.bottom.toInt() - 3 step 8) {
                val color = shot.getPixel(x, sampleY)
                val red = android.graphics.Color.red(color)
                val green = android.graphics.Color.green(color)
                assertFalse("A dark video matte must not appear at $frame ms / y=$sampleY",
                    red < 180 && green < 180 && kotlin.math.abs(red - green) < 16)
            }
            for (sampleX in listOf(nav.left + 4, nav.center.x, nav.right - 4)) {
                assertEquals("Video must never paint over navigation at $frame ms", before.getPixel(sampleX.toInt(), nav.center.y.toInt()),
                    shot.getPixel(sampleX.toInt(), nav.center.y.toInt()))
            }
            val movingArt = rule.onAllNodesWithTag("mini-player-artwork", true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val movingVideo = rule.onAllNodesWithTag("media-player").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            if (movingArt != null && movingVideo != null && frame < 160) {
                val a = movingArt.top - art.top
                val b = movingVideo.top - media.top
                maxShifts.add(kotlin.math.abs(a - b))
                val expectedAlpha = 1f - b / (24f * rule.activity.resources.displayMetrics.density)
                rule.runOnUiThread { assertEquals("Native alpha and movement share the same clock", expectedAlpha,
                    checkNotNull(nativeView).alpha, .04f) }
            }
            shot.recycle()
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("mini-player").assertDoesNotExist()
        rule.onNodeWithTag("media-player").assertDoesNotExist()
        assertTrue("Intermediate frames must be present", maxShifts.size >= 3)
        assertTrue("Video and chrome must move together", maxShifts.all { it <= 2f })
        rule.runOnIdle { assertEquals(1, closes) }
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("search-field").assertIsDisplayed()
        before.recycle()
    }
    private fun screenshot(name: String): Bitmap {
        rule.waitForIdle(); android.os.SystemClock.sleep(60)
        val i = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(i.uiAutomation.takeScreenshot())
        File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
