package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** In-memory screens and an idle player: never read or mutate a real user library. */
class AppNavigationRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val video = VideoUiModel("fixture-video", "Fixture video", "Creator", "", "1:00")

    @After fun releasePlayer() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    private fun launch(state: androidx.compose.runtime.MutableState<GrayjayUiState>, actions: GrayjayAppActions = GrayjayAppActions()) {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        actions.onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
    }

    @Test fun enteringAndReturningToSearchNeverRequestsTextFocus() {
        val state = mutableStateOf(GrayjayUiState())
        launch(state)
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("search-field").assertIsDisplayed().assertIsNotFocused()
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("search-field").assertIsNotFocused()
    }

    @Test fun miniplayerCloseDoesNotJumpAwayFromPreferencesToItsPlaylist() {
        val playlist = PlaylistUiModel("fixture-playlist", "Fixture playlist", "", listOf(video.id))
        val state = mutableStateOf(GrayjayUiState(
            videos = listOf(video), playlists = listOf(playlist), playbackPlaylist = playlist,
            playback = PlaybackUiState(currentVideoId = video.id), nowPlaying = NowPlayingUiState(video = video),
        ))
        val actions = GrayjayAppActions().apply {
            onClosePlayback = { state.value = state.value.copy(playback = PlaybackUiState(), nowPlaying = NowPlayingUiState()) }
        }
        launch(state, actions)
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("mini-player-close").performClick()
        rule.onNodeWithTag("nav-settings").assertIsSelected()
        rule.onNodeWithTag("playlist-detail-${playlist.id}").assertDoesNotExist()
    }

    @Test fun headerBackClearsPlaylistSelectionBeforeLeavingPlaylist() {
        val playlist = PlaylistUiModel("fixture-playlist", "Fixture playlist", "", listOf(video.id))
        val state = mutableStateOf(GrayjayUiState(
            videos = listOf(video), libraryVideos = listOf(video), playlists = listOf(playlist),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Playlist, playlist.id),
        ))
        launch(state)
        rule.onNodeWithTag("playlist-detail-${playlist.id}")
            .performScrollToNode(hasTestTag("video-card-${video.id}"))
        rule.onNodeWithTag("video-card-${video.id}").performTouchInput { longClick() }
        rule.onNodeWithTag("playlist-selection-bar").assertIsDisplayed()
        rule.onNodeWithTag("browse-back").performClick()
        rule.onNodeWithTag("playlist-selection-bar").assertDoesNotExist()
        rule.onNodeWithTag("playlist-detail-${playlist.id}").assertIsDisplayed()
        rule.onNodeWithTag("browse-back").performClick()
        rule.onNodeWithTag("playlist-detail-${playlist.id}").assertDoesNotExist()
    }

    @Test fun cancelledMiniplayerExpansionKeepsTheCurrentBrowsePage() {
        val playlist = PlaylistUiModel("fixture-playlist", "Fixture playlist", "", listOf(video.id))
        val state = mutableStateOf(GrayjayUiState(
            videos = listOf(video), playlists = listOf(playlist), playbackPlaylist = playlist,
            playback = PlaybackUiState(currentVideoId = video.id), nowPlaying = NowPlayingUiState(video = video),
        ))
        launch(state)
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("mini-player").performTouchInput {
            swipe(center, center - Offset(0f, 80f), durationMillis = 800)
        }
        rule.onNodeWithTag("settings-list").assertIsDisplayed()
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        rule.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test fun activeJobShortcutReturnsToItsActualOrigin() {
        val state = mutableStateOf(GrayjayUiState(youtubeImport = YoutubeImportUiState(isRunning = true)))
        launch(state)
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("active-jobs-toggle").performClick()
        rule.onNodeWithTag("active-job-card-youtube-import").performClick()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("nav-library").assertIsSelected()
    }

    @Test fun restoringActivityKeepsExpandedPlayerOpen() {
        val state = mutableStateOf(GrayjayUiState(
            videos = listOf(video), playback = PlaybackUiState(currentVideoId = video.id),
            nowPlaying = NowPlayingUiState(video = video),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id),
        ))
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        val restoration = StateRestorationTester(rule)
        restoration.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
    }

    @Test fun channelRouteReloadsAfterRestorationWithAnEmptyViewModelCatalog() {
        val channel = ChannelUiModel("https://custom.example/creator", "Unfollowed creator",
            "custom-source", "Custom source", 0, "", "")
        val state = mutableStateOf(GrayjayUiState(
            channelDetail = ChannelDetailUiState(channelId = channel.id, channel = channel),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Channel, channel.id),
        ))
        val reloaded = mutableListOf<ChannelUiModel>()
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val actions = GrayjayAppActions().apply {
            onLoadChannel = { reloaded += it }
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        val restoration = StateRestorationTester(rule)
        restoration.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        rule.onNodeWithTag("channel-detail-${channel.id}").assertIsDisplayed()
        rule.runOnUiThread { state.value = state.value.copy(channels = emptyList(), channelDetail = ChannelDetailUiState()) }
        rule.waitForIdle()
        val beforeRestore = reloaded.size
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertTrue("Restored route must reload even without known channels", reloaded.size > beforeRestore)
            assertEquals(channel.id, reloaded.last().id)
            assertEquals(channel.sourceId, reloaded.last().sourceId)
        }
    }

    @Test fun remotePlaylistRouteReloadsAfterRestorationWithoutTheLoadedPlaylist() {
        val playlist = PlaylistUiModel("https://custom.example/list", "Remote list", "", emptyList(), sourceId = "custom-source")
        val state = mutableStateOf(GrayjayUiState(
            remotePlaylistDetail = RemotePlaylistDetailUiState(playlist = playlist),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Playlist, playlist.id),
        ))
        val reloaded = mutableListOf<PlaylistUiModel>()
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val actions = GrayjayAppActions().apply {
            onLoadRemotePlaylist = { reloaded += it }
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        val restoration = StateRestorationTester(rule)
        restoration.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        rule.waitForIdle()
        rule.runOnUiThread { state.value = state.value.copy(playlists = emptyList(), remotePlaylistDetail = RemotePlaylistDetailUiState()) }
        rule.waitForIdle()
        val beforeRestore = reloaded.size
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertTrue("Restored route must reload even without playlist details", reloaded.size > beforeRestore)
            assertEquals(playlist.id, reloaded.last().id)
            assertEquals(playlist.sourceId, reloaded.last().sourceId)
            assertTrue(reloaded.last().videoIds.isEmpty())
        }
    }
}
