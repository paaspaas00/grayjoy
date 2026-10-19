package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class YoutubeAutoplayChannelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val channel = ChannelUiModel("fixture-artist", "Artist", "youtube", "YouTube", 0, "", "")
    private val videos = (1..3).map { VideoUiModel("item-$it", "Video $it", "Artist", "", "1:00") }

    @Test fun playSelectionUsesScreenOrderAndLeavesSelectionMode() {
        var played = emptyList<String>()
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, ChannelDetailUiState(channel = channel, videos = videos), false, {},
                onTabSelected = {}, onPlaylistClick = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {},
                onPlaySelection = { played = it })
        } }
        val list = rule.onNodeWithTag("channel-detail-${channel.id}")
        list.performScrollToNode(hasTestTag("video-card-${videos[1].id}"))
        rule.onNodeWithTag("video-card-${videos[1].id}").performTouchInput { longClick() }
        list.performScrollToNode(hasTestTag("video-card-${videos[0].id}"))
        rule.onNodeWithTag("video-card-${videos[0].id}").performClick()
        rule.onNodeWithTag("channel-selection-play").performClick()
        rule.runOnIdle { assertEquals(listOf(videos[0].id, videos[1].id), played) }
        rule.onNodeWithTag("channel-selection-bar").assertDoesNotExist()
    }

    @Test fun releasesDisplayTheirOwnPlaylistsAndOpenTheAlbum() {
        val album = PlaylistUiModel("album-id", "Album fixture", "", emptyList())
        var opened: PlaylistUiModel? = null
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, ChannelDetailUiState(channel = channel, selectedTab = ChannelContentTab.Releases,
                supportsReleases = true, releases = listOf(album)), false, {}, onTabSelected = {},
                onPlaylistClick = { opened = it }, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
        } }
        rule.onNodeWithTag("channel-tab-releases").assertIsSelected()
        rule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasText("Album fixture"))
        rule.onNodeWithText("Album fixture").performClick()
        rule.runOnIdle { assertEquals(album.id, opened?.id) }
        rule.onNodeWithTag("channel-video-search").assertDoesNotExist()
    }

    @Test fun countdownShowsTheNextVideoAndCancelDismissesIt() {
        val state = mutableStateOf<YoutubeAutoplayUiState?>(YoutubeAutoplayUiState("Next video", 10))
        var cancelled = false
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalYoutubeAutoplay provides YoutubeAutoplayControls(state.value, { cancelled = true; state.value = null })) {
                YoutubeAutoplayPrompt(Modifier)
            }
        } }
        rule.onNodeWithTag("youtube-autoplay-countdown").assertIsDisplayed()
        rule.onNodeWithText("Next video").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value?.copy(seconds = 9) }
        rule.onNodeWithTag("youtube-autoplay-cancel").performClick()
        rule.runOnIdle { assertTrue(cancelled) }
        rule.onNodeWithTag("youtube-autoplay-countdown").assertDoesNotExist()
    }
}
