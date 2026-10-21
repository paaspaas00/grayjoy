package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.screens.SearchScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Rule
import org.junit.Test

class DuplicateContentRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val video = VideoUiModel("duplicate-video", "Fixture video", "Creator", "", "1:00")
    private val channel = ChannelUiModel("duplicate-channel", "Fixture creator", "youtube", "YouTube", 0, "", "")
    private val playlist = PlaylistUiModel("duplicate-playlist", "Fixture album", "", emptyList())

    @Test fun repeatedBackendSearchItemsDoNotCrashAnyResultTab() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            SearchScreen(SearchUiState(query = "fixture", hasSearched = true, videos = List(4) { video },
                channels = List(4) { channel }, playlists = List(4) { playlist }), emptyList(),
                onQueryChange = {}, onSubmit = { _, _, _ -> }, onLoadMore = {}, onVideoClick = {},
                onVideoLongClick = {}, onChannelClick = {}, onPlaylistClick = {})
        } }
        val list = rule.onNodeWithTag("search-results")
        list.performScrollToNode(hasTestTag("video-card-${video.id}"))
        rule.onAllNodesWithTag("video-card-${video.id}").assertCountEquals(1)
        list.performScrollToIndex(0)
        rule.onNodeWithTag("search-filter-creators").performClick()
        list.performScrollToNode(hasText(channel.name))
        rule.onAllNodesWithText(channel.name).assertCountEquals(1)
        list.performScrollToIndex(0)
        rule.onNodeWithTag("search-filter-playlists").performClick()
        list.performScrollToNode(hasText(playlist.title))
        rule.onAllNodesWithText(playlist.title).assertCountEquals(1)
    }

    @Test fun repeatedChannelVideosDoNotProduceDuplicateLazyKeys() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, ChannelDetailUiState(channel = channel, videos = List(8) { video }),
                false, {}, onTabSelected = {}, onPlaylistClick = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
        } }
        rule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasTestTag("video-card-${video.id}"))
        rule.onAllNodesWithTag("video-card-${video.id}").assertCountEquals(1)
    }

    @Test fun repeatedReleasesDoNotProduceDuplicateLazyKeys() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, ChannelDetailUiState(channel = channel, selectedTab = ChannelContentTab.Releases,
                supportsReleases = true, releases = List(8) { playlist }), false, {}, onTabSelected = {},
                onPlaylistClick = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
        } }
        rule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasText(playlist.title))
        rule.onAllNodesWithText(playlist.title).assertCountEquals(1)
    }
}
