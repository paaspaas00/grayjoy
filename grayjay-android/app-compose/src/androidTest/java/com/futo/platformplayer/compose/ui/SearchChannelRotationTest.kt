package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.screens.SearchScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchChannelRotationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    private val videos = (1..5).map { VideoUiModel("test-$it", "Video $it", "Creator", "", "2:00") }
    @After fun release() { rule.runOnUiThread { player?.release() } }

    @Test fun searchPlayAllPreservesResultOrderAndExcludesUnavailableVideos() {
        var played = emptyList<String>()
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            SearchScreen(SearchUiState(query = "fixture", hasSearched = true,
                videos = listOf(videos[2], videos[0].copy(isAvailable = false), videos[1])), emptyList(),
                onQueryChange = {}, onSubmit = { _, _, _ -> }, onLoadMore = {}, onVideoClick = {},
                onVideoLongClick = {}, onChannelClick = {}, onPlaylistClick = {}, onPlayAll = { played = it })
        } }
        rule.onNodeWithTag("search-results").performScrollToNode(hasTestTag("search-play-all"))
        rule.onNodeWithTag("search-play-all").performClick()
        rule.runOnIdle { assertEquals(listOf(videos[2].id, videos[1].id), played) }
    }

    @Test fun channelSelectionAddsMultipleVideosAndBackCancelsBeforeLeaving() {
        val channel = ChannelUiModel(id = "fixture", name = "Creator", sourceId = "youtube", source = "YouTube", unreadCount = 0, followerCount = "", description = "")
        val detail = mutableStateOf(ChannelDetailUiState(channelId = channel.id, channel = channel,
            videos = videos, shorts = videos, supportsShorts = true))
        var added = emptyList<String>()
        var played = false
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, detail.value, false, {},
                onTabSelected = { detail.value = detail.value.copy(selectedTab = it) }, onPlaylistClick = {},
                onLoadMore = {}, onVideoClick = { played = true }, onVideoLongClick = {},
                onAddSelectionToPlaylist = { added = it })
        } }
        val list = rule.onNodeWithTag("channel-detail-fixture")
        list.performScrollToNode(hasTestTag("video-card-${videos[0].id}"))
        rule.onNodeWithTag("video-channel-footer-${videos[0].id}", useUnmergedTree = true).performTouchInput { longClick() }
        list.performScrollToNode(hasTestTag("video-card-${videos[1].id}"))
        rule.onNodeWithTag("video-card-${videos[1].id}").performClick()
        rule.onNodeWithTag("channel-selection-bar").assertIsDisplayed()
        rule.onNodeWithTag("channel-selection-add").performClick()
        rule.runOnIdle { assertEquals(listOf(videos[0].id, videos[1].id), added); assertFalse(played) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("channel-selection-bar").assertDoesNotExist()
        rule.runOnIdle { detail.value = detail.value.copy(selectedTab = ChannelContentTab.Shorts) }
        list.performScrollToNode(hasTestTag("video-card-${videos[0].id}"))
        rule.onNodeWithTag("video-card-${videos[0].id}").performTouchInput { longClick() }
        rule.onNodeWithTag("channel-selection-cancel").performClick()
        rule.onNodeWithTag("channel-selection-bar").assertDoesNotExist()
    }

    @Test fun rotatingOnlyEntersFullscreenWhenAndroidAllowsItAndNowPlayingIsExpanded() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val video = videos.first()
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video), nowPlaying = NowPlayingUiState(video = video),
            playback = PlaybackUiState(currentVideoId = video.id, currentVideoWidth = 1280, currentVideoHeight = 720),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        val landscape = mutableStateOf(true)
        val allowed = mutableStateOf(false)
        var fullscreen = false
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
            onFullscreenPresentationChanged = { full, _ -> fullscreen = full }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GrayjayApp(state.value, player!!, actions, deviceIsLandscape = landscape.value,
                automaticPlayerRotationEnabled = allowed.value)
        } }
        rule.runOnIdle { assertFalse(fullscreen); allowed.value = true }
        rule.runOnIdle { assertTrue(fullscreen); allowed.value = false }
        rule.runOnIdle { assertTrue(fullscreen); allowed.value = true; landscape.value = false }
        rule.runOnIdle { assertFalse(fullscreen) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.runOnIdle { landscape.value = true }
        rule.runOnIdle { assertFalse(fullscreen) }
    }
}
