package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated history and queue; no access to the installed user's library. */
class MiniplayerQueueRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    @Test fun historyEnqueueIgnoresTapsButAllowsSwipingBeforeQueuedSourcesAreReady() = checkQueue(false)
    @Test fun audioOnlyHistoryEnqueueIgnoresTapsButAllowsSwipingBeforeQueuedSourcesAreReady() = checkQueue(true)

    @Test fun loadingVideoStaysMinimizedWhenItsPlaybackAndExtrasArrive() = checkLoadingCompletion(false)
    @Test fun loadingVideoDoesNotReopenAfterNavigatingToAnotherTab() = checkLoadingCompletion(true)

    private fun checkLoadingCompletion(changeTab: Boolean) {
        val previous = VideoUiModel("previous", "Previous video", "Creator", "", "3:00")
        val opening = VideoUiModel("opening", "Still loading", "Creator", "", "3:00")
        val state = mutableStateOf(GrayjayUiState(videos = listOf(previous, opening),
            home = HomeUiState(videos = listOf(opening)),
            playback = PlaybackUiState(currentVideoId = previous.id, queueVideoIds = listOf(previous.id)),
            nowPlaying = NowPlayingUiState(video = previous)))
        val actions = GrayjayAppActions().apply {
            onOpenVideo = { id ->
                assertEquals(opening.id, id)
                // Keep the previous player item alive while the new source is resolving.
                state.value = state.value.copy(nowPlaying = NowPlayingUiState(
                    video = opening, isLoadingPlayback = true, isLoadingExtras = true))
            }
        }
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        rule.onNodeWithTag("video-title-${opening.id}", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
        rule.onNodeWithTag("media-player").performTouchInput { swipe(center, center + Offset(0f, 1300f), 700) }
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        if (changeTab) rule.onNodeWithTag("nav-search").performClick()
        rule.runOnIdle {
            val resolved = opening.copy(title = "Resolved title")
            state.value = state.value.copy(
                playback = PlaybackUiState(currentVideoId = resolved.id, queueVideoIds = listOf(resolved.id), isPlaying = true),
                nowPlaying = state.value.nowPlaying.copy(video = resolved, isLoadingPlayback = false))
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        if (changeTab) rule.onNodeWithTag("search-field").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(nowPlaying = state.value.nowPlaying.copy(isLoadingExtras = false)) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        rule.onNodeWithTag("mini-player").performTouchInput { click(Offset(width * .4f, height * .5f)) }
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(center, center.copy(y = -1200f), 500) }
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
    }

    private fun checkQueue(audioOnly: Boolean) {
        val videos = (0..3).map { index -> VideoUiModel("queue-fixture-$index", "Video $index", "Creator", "", "3:00",
            lastWatchedAt = 10_000L - index, playbackAudioOnly = index == 0 && audioOnly) }
        val current = videos.first()
        val state = mutableStateOf(GrayjayUiState(videos = videos, libraryVideos = videos,
            playback = PlaybackUiState(currentVideoId = current.id, queueVideoIds = listOf(current.id), isPlaying = true),
            nowPlaying = NowPlayingUiState(video = current),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, current.id)))
        var enqueued = emptyList<String>()
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
            onQueueVideos = { ids ->
                enqueued = ids
                val playlist = PlaylistUiModel("queue-list", "In coda", "", listOf(current.id) + ids, isPlaybackQueue = true)
                // Publish the logical queue, but deliberately never resolve the queued sources.
                state.value = state.value.copy(temporaryPlaybackQueue = playlist,
                    playback = state.value.playback.copy(fullQueueVideoIds = playlist.videoIds))
            }
        }
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        rule.onNodeWithTag("media-player").performTouchInput { swipe(center, center + Offset(0f, 1300f), 700) }
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("video-card-${videos[1].id}").performScrollTo().performTouchInput { longClick() }
        for (item in videos.drop(2)) rule.onNodeWithTag("video-card-${item.id}").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithTag("history-enqueue").performClick()
        rule.runOnIdle { assertEquals(videos.drop(1).map { it.id }.toSet(), enqueued.toSet()) }
        rule.onNodeWithTag("mini-player").performTouchInput { click(Offset(width * .4f, height * .5f)) }
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(center, center.copy(y = -1200f), 500) }
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
        rule.onNodeWithTag("media-player").performTouchInput { swipe(center, center + Offset(0f, 1300f), 700) }
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(Offset(width * .4f, height * .5f), Offset(width * .4f, -1000f), 700) }
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
        rule.onNodeWithTag("media-player").performTouchInput { swipe(center, center + Offset(0f, 1300f), 700) }
        rule.onNodeWithTag("mini-player").performTouchInput { click(Offset(width * .1f, height * .5f)) }
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        assertEquals(emptyList<PlaylistUiModel>(), state.value.playlists)
    }
}
