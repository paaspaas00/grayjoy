package com.futo.platformplayer.compose.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.DevicePerformanceProfile
import com.futo.platformplayer.compose.LocalDevicePerformanceProfile
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercise both layout policies on the connected device without changing its system DPI or user data. */
class SelectionBarPlacementTest {
    private var currentLayout = "standard"
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val channel = ChannelUiModel("channel", "Creator", "youtube", "YouTube", 0, "2", "")
    private val videos = List(30) { VideoUiModel("selection-$it", "Video $it", "Creator", "", "2:00",
        channelId = channel.id, authorUrl = channel.id, lastWatchedAt = 1000L - it, isDownloaded = true) }
    private val playlist = PlaylistUiModel("selection-list", "Playlist", "", videos.map { it.id })

    @Test fun libraryBarsStayDockedOnModernAndCompactPhonesWithAndWithoutMiniplayer() = matrix { playing ->
        rule.onNodeWithTag("nav-library").performClick()
        for (tab in listOf("history", "downloads", "playlists")) {
            rule.onNodeWithTag("library-filter-strip").performScrollToNode(hasTestTag("library-filter-$tab"))
            rule.onNodeWithTag("library-filter-$tab").performClick()
            val target = if (tab == "playlists") "playlist-${playlist.id}" else "video-card-${videos.first().id}"
            rule.onNodeWithTag(target).performScrollTo().performTouchInput { longClick() }
            assertDocked("$tab-selection-bar")
            playing.value = false
            rule.waitForIdle()
            assertDocked("$tab-selection-bar", mini = false)
            playing.value = true
            rule.onNodeWithTag("library-selection-cancel").performClick()
        }
    }

    @Test fun playlistAndChannelBarsReserveSpaceAtTheBottomOnBothPhoneLayouts() = matrix { _ ->
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("library-filter-playlists").performScrollTo().performClick()
        rule.onNodeWithTag("playlist-${playlist.id}").performScrollTo().performClick()
        rule.onNodeWithTag("video-card-${videos.first().id}").performScrollTo().performTouchInput { longClick() }
        assertDocked("playlist-selection-bar")
        rule.onNodeWithTag("playlist-detail-${playlist.id}").performScrollToIndex(31)
        val bottomItem = rule.onNodeWithTag("video-card-${videos.last().id}").fetchSemanticsNode().boundsInRoot
        assertTrue(bottomItem.bottom <= rule.onNodeWithTag("playlist-selection-bar").fetchSemanticsNode().boundsInRoot.top + 1)
        rule.onNodeWithTag("playlist-selection-cancel").performClick()
        rule.onNodeWithTag("browse-back").performClick()
        rule.onNodeWithTag("nav-home").performClick()
        rule.onNodeWithTag("video-channel-footer-${videos.first().id}").performClick()
        rule.onNodeWithTag("video-card-${videos.first().id}").performScrollTo().performTouchInput { longClick() }
        assertDocked("channel-selection-bar")
    }

    @Test fun subscriptionAndManagementBarsStayBelowBothListsAndGrids() = matrix { _ ->
        rule.onNodeWithTag("nav-subscriptions").performClick()
        rule.onNodeWithTag("video-card-${videos.first().id}").performScrollTo().performTouchInput { longClick() }
        assertDocked("subscriptions-selection-bar")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(rule.activity.getString(R.string.manage)).performScrollTo().performClick()
        rule.onNodeWithTag("manage-subscription-${channel.id}").performTouchInput { longClick() }
        assertDocked("manage-subscriptions-selection-header")
        rule.onNodeWithTag("manage-subscriptions-layout").performClick()
        assertDocked("manage-subscriptions-selection-header")
    }

    private fun assertDocked(tag: String, mini: Boolean = true, padding: Float = 1f) {
        rule.waitForIdle()
        val bar = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag(if (mini) "mini-player" else "bottom-navigation-bar").fetchSemanticsNode().boundsInRoot
        assertEquals("$tag must sit immediately above the player/navigation", dock.top, bar.bottom, padding)
        assertTrue(bar.height > 40 && bar.top > dock.top / 2)
        val mode = currentLayout
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "selection-$tag-$mode-$mini.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }; bitmap.recycle()
        }
    }

    private fun matrix(test: (MutableState<Boolean>) -> Unit) {
        val lowEnd = mutableStateOf(false)
        val playing = mutableStateOf(true)
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        try {
            rule.setContent { key(lowEnd.value) {
                val original = LocalConfiguration.current
                val width = if (lowEnd.value) 360 else 412
                val scale = rule.activity.resources.displayMetrics.widthPixels / width.toFloat()
                val config = Configuration(original).apply { screenWidthDp = width; smallestScreenWidthDp = width;
                    screenHeightDp = (rule.activity.resources.displayMetrics.heightPixels / scale).toInt() }
                CompositionLocalProvider(LocalDevicePerformanceProfile provides DevicePerformanceProfile(lowEnd.value, lowEnd.value, !lowEnd.value),
                    LocalConfiguration provides config, LocalDensity provides Density(scale)) {
                    GrayjayTheme(dynamicColor = false) {
                        GrayjayApp(GrayjayUiState(videos = videos, libraryVideos = videos, home = HomeUiState(videos = videos),
                            playlists = listOf(playlist), channels = listOf(channel), followedCreatorIds = setOf(channel.id),
                            followingVideos = videos, followingFeedLoaded = true,
                            channelDetail = ChannelDetailUiState(channelId = channel.id, channel = channel, videos = videos),
                            sources = listOf(SourceUiModel("youtube", "YouTube", "", 0L, true)),
                            playback = if (playing.value) PlaybackUiState(currentVideoId = videos.first().id) else PlaybackUiState(),
                            nowPlaying = if (playing.value) NowPlayingUiState(video = videos.first()) else NowPlayingUiState()),
                            player, GrayjayAppActions())
                    }
                }
            } }
            for (mode in listOf(false, true)) {
                currentLayout = if (mode) "compact" else "standard"
                rule.runOnIdle { lowEnd.value = mode; playing.value = true }; test(playing)
            }
        } finally { rule.runOnUiThread { player.release() } }
    }
}
