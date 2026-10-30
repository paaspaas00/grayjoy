package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.ProfileSwitcherDialogs
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NowPlayingHeaderAndFrameUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @Before fun setup() = rule.runOnUiThread { rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity).build() }
    @After fun cleanup() = rule.runOnUiThread { player.release() }
    private val video = VideoUiModel("header-video", "A title above the video", "A channel", "Metadata", "3:00")

    @Test fun theTopNowPlayingHeaderUsesThePlayerMinimizeGesture() = minimizeFromTitle(false)
    @Test fun theOneHandedVideoTitleUsesThePlayerMinimizeGesture() = minimizeFromTitle(true)
    private fun minimizeFromTitle(oneHanded: Boolean) {
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video), oneHandedPlayerEnabled = oneHanded,
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id)),
            nowPlaying = NowPlayingUiState(video = video), externalNavigation = ExternalNavigationUiModel(1L, ExternalNavigationKind.Video, video.id)))
        val actions = GrayjayAppActions().apply { onExternalNavigationHandled = { state = state.copy(externalNavigation = null) } }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, actions) } }
        val tag = if (oneHanded) "one-handed-video-info" else "now-playing-header"
        rule.onNodeWithTag(tag).assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(0f, 1500f), 600)
        }
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        capture("minimize-title-$oneHanded")
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(center, center.copy(y = -1500f), 600) }
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
    }

    @Test fun previousFrameStaysHiddenAfterTheCardMorphAndAfterTheNewItemIsSelected() {
        val pkg = InstrumentationRegistry.getInstrumentation().context.packageName
        val new = video.copy(id = "new-frame", title = "The requested new video", thumbnailUrl = "android.resource://$pkg/drawable/audit_earth")
        val old = video.copy(id = "old-frame", title = "Previously playing")
        rule.runOnUiThread { player.setMediaItem(MediaItem.Builder().setMediaId(old.id).setUri("https://fixture.invalid/old").build()) }
        var state by mutableStateOf(GrayjayUiState(videos = listOf(new, old), home = HomeUiState(videos = listOf(new)),
            nowPlaying = NowPlayingUiState(video = old), playback = PlaybackUiState(currentVideoId = old.id)))
        val actions = GrayjayAppActions().apply { onOpenVideo = {
            state = state.copy(nowPlaying = NowPlayingUiState(video = new, isLoadingPlayback = true))
        } }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("video-title-${new.id}", true).performClick()
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
        rule.onNodeWithTag("video-awaiting-frame").assertIsDisplayed()
        fun nativeAlpha(): Float {
            var alpha = -1f
            rule.runOnUiThread {
                fun find(view: android.view.View) {
                    if (view is androidx.media3.ui.PlayerView && view.player === player) alpha = view.alpha
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))
                }
                find(rule.activity.window.decorView)
            }
            return alpha
        }
        assertEquals(0f, nativeAlpha(), .001f)
        capture("new-video-old-frame-hidden")
        rule.runOnUiThread {
            player.setMediaItem(MediaItem.Builder().setMediaId(new.id).setUri("https://fixture.invalid/new").build())
            state = state.copy(nowPlaying = state.nowPlaying.copy(isLoadingPlayback = false), playback = state.playback.copy(currentVideoId = new.id))
        }
        rule.onNodeWithTag("video-awaiting-frame").assertIsDisplayed()
        assertEquals("Changing the ID is not enough: the first new frame must actually arrive", 0f, nativeAlpha(), .001f)
    }

    @Test fun profileChoicesAreClippedIntoARoundedGroupInBothThemes() {
        var dark by mutableStateOf(false)
        val profiles = (1..3).map { ProfileUiModel("profile-$it", "Profile $it", ProfileProtection.None) }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = dark) {
            ProfileSwitcherDialogs(profiles, profiles.first().id, true, {}, {}, { _, _ -> }, { _, _ -> false }, bypassProtection = true)
        } }
        rule.onNodeWithTag("profile-profile-1").assertIsDisplayed()
        rule.onNodeWithTag("profile-profile-3").assertIsDisplayed()
        capture("rounded-profile-choices-light")
        rule.runOnIdle { dark = true }
        capture("rounded-profile-choices-dark")
    }

    private fun capture(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(60)
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
