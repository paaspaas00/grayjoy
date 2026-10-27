package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Only synthetic state is used; the installed library and preferences are never modified. */
class October27NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val video = VideoUiModel("v", "A video title with useful context", "Example creator", "123 views · Today", "3:00")
    @After fun cleanup() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }
    private fun launch(state: MutableState<GrayjayUiState>, actions: GrayjayAppActions = GrayjayAppActions()) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity).build() }
        actions.onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File("/sdcard/Download/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun openingVideoIsExpandedOnTheFirstFrameWithoutMorphingFromMini() {
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video), home = HomeUiState(videos = listOf(video))))
        launch(state, GrayjayAppActions().apply { onOpenVideo = {
            state.value = state.value.copy(nowPlaying = NowPlayingUiState(video = video, isLoadingPlayback = true))
        } })
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("video-card-v").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
        // Shared video bounds are measured on the first frame; the decoder surface attaches
        // on the next one. The screen must already be expanded before those bounds exist.
        rule.mainClock.advanceTimeByFrame()
        val bounds = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Player must begin expanded, not at the mini slot: $bounds", bounds.top < 500 && bounds.height > 350)
        capture("oct27-direct-opening")
        rule.mainClock.autoAdvance = true
    }

    @Test fun experimentalOneHandedInfoIsAbovePlayerAndDoesNotDuplicateTitle() {
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video), oneHandedPlayerEnabled = true,
            nowPlaying = NowPlayingUiState(video = video), playback = PlaybackUiState(currentVideoId = video.id),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        launch(state)
        rule.mainClock.advanceTimeBy(400)
        val info = rule.onNodeWithTag("one-handed-video-info").fetchSemanticsNode().boundsInRoot
        val surface = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Info belongs above the player", info.bottom <= surface.top + 1)
        rule.onAllNodesWithText(video.title).assertCountEquals(1)
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.watch_later)).assertIsDisplayed()
        capture("oct27-one-handed-player")
        rule.runOnUiThread { state.value = state.value.copy(oneHandedPlayerEnabled = false) }
        rule.onNodeWithTag("one-handed-video-info").assertDoesNotExist()
        assertTrue(rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot.top < surface.top)
    }

    @Test fun libraryTabClicksDoNotMoveTheTabStrip() {
        launch(mutableStateOf(GrayjayUiState()))
        rule.onNodeWithTag("nav-library").performClick()
        val before = rule.onNodeWithTag("library-filter-history").fetchSemanticsNode().boundsInRoot.left
        rule.onNodeWithTag("library-filter-playlists").performClick()
        rule.onNodeWithTag("library-filter-playlists").assertIsSelected()
        assertEquals(before, rule.onNodeWithTag("library-filter-history").fetchSemanticsNode().boundsInRoot.left, .5f)
    }

    @Test fun predictiveBackRevealsDestinationCancelsAndCommitsWithoutAnotherEntryAnimation() {
        val playlist = PlaylistUiModel("p", "An example playlist", "", listOf(video.id))
        launch(mutableStateOf(GrayjayUiState(videos = listOf(video), libraryVideos = listOf(video), playlists = listOf(playlist),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Playlist, playlist.id))))
        rule.onNodeWithTag("playlist-detail-p").assertIsDisplayed()
        fun progress(edge: Int) {
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, edge)) }
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 410f, .55f, edge)) }
            rule.waitForIdle()
        }
        progress(BackEventCompat.EDGE_LEFT)
        rule.onNodeWithTag("back-preview-page", useUnmergedTree = true).assertExists()
        capture("oct27-predictive-back-middle")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist-detail-p").assertIsDisplayed()
        rule.onNodeWithTag("back-preview-page", useUnmergedTree = true).assertDoesNotExist()
        progress(BackEventCompat.EDGE_RIGHT)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist-detail-p").assertDoesNotExist()
        rule.onNodeWithTag("library-filter-playlists").assertIsSelected()
        rule.onNodeWithTag("back-preview-page", useUnmergedTree = true).assertDoesNotExist()
        capture("oct27-predictive-back-complete")
    }
}
