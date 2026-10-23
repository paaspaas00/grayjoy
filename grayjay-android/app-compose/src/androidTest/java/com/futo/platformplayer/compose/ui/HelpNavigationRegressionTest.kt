package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HelpNavigationRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    @Test fun helpIsOnTheLeftOfEveryMainRouteAndBothPlayerModes() {
        val video = VideoUiModel("help-video", "Video", "Creator", "", "3:00")
        val channel = ChannelUiModel("help-channel", "Creator", "youtube", "YouTube", 0, "", "")
        val playlist = PlaylistUiModel("help-playlist", "Playlist", "", listOf(video.id))
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video), libraryVideos = listOf(video),
            channels = listOf(channel), playlists = listOf(playlist)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        fun assertHelpLeft() {
            val button = rule.onNodeWithTag("open-help").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("Help should be in the left half: $button / $screen", button.center.x < screen.center.x)
        }
        for (tab in listOf("home", "subscriptions", "library", "settings", "search")) {
            rule.onNodeWithTag("nav-$tab").performClick()
            assertHelpLeft()
        }
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("manage-sources"))
        rule.onNodeWithTag("manage-sources").performClick()
        assertHelpLeft()
        rule.runOnUiThread { state.value = state.value.copy(externalNavigation = ExternalNavigationUiModel(2, ExternalNavigationKind.Channel, channel.id)) }
        assertHelpLeft()
        rule.runOnUiThread { state.value = state.value.copy(externalNavigation = ExternalNavigationUiModel(3, ExternalNavigationKind.Playlist, playlist.id)) }
        assertHelpLeft()
        rule.runOnUiThread { state.value = state.value.copy(nowPlaying = NowPlayingUiState(video = video),
            playback = PlaybackUiState(currentVideoId = video.id, currentVideoWidth = 1080, currentVideoHeight = 1920),
            externalNavigation = ExternalNavigationUiModel(4, ExternalNavigationKind.Video, video.id)) }
        assertHelpLeft()
        rule.onNodeWithTag("player-fullscreen").performClick()
        assertHelpLeft()
        rule.onNodeWithTag("open-help").performClick()
        rule.onNodeWithTag("help-topics").assertIsDisplayed()
    }
}
