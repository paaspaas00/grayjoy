package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.HomeScreen
import com.futo.platformplayer.compose.ui.screens.SubscriptionsScreen
import com.futo.platformplayer.compose.ui.screens.VideoDetailScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Screen fixtures only: no real profiles, subscriptions or playlists are modified. */
class FeedAndCommentsRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val video = VideoUiModel("fixture-video", "Video", "Creator", "", "1:00")

    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    @Test fun followedVideosCanRefreshAfterFirstLoadAndCannotDoubleSubmit() {
        val loading = mutableStateOf(false)
        var requests = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            SubscriptionsScreen(emptyList(), emptyList(), emptySet(), { _, _ -> }, {}, {}, {}, {},
                completeFeedLoaded = true, completeFeedLoading = loading.value,
                onRequestCompleteFeed = { error("Already loaded") },
                onRefresh = { requests++; loading.value = true })
        } }
        rule.onNodeWithTag("subscriptions-refresh").performClick()
        rule.onNodeWithTag("subscriptions-refresh").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, requests); loading.value = false }
        rule.onNodeWithTag("subscriptions-refresh").performClick()
        rule.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun pullToRefreshWorksFromEmptyHome() {
        val state = mutableStateOf(HomeUiState(subscriptionsTotal = 5))
        var requests = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            HomeScreen(home = state.value, onFeedSelected = {}, onRefresh = {
                requests++
                state.value = state.value.copy(videos = listOf(video))
            }, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
        } }
        rule.onNodeWithTag("home-pull-to-refresh").performTouchInput { swipeDown(durationMillis = 700) }
        rule.waitUntil(5_000) { requests > 0 }
        rule.onNodeWithTag("video-card-${video.id}").assertIsDisplayed()
    }

    @Test fun changingEnabledSourcesKeepsPagerAndSelectedTabInSync() {
        val source = SourceUiModel("youtube", "YouTube", "", 0L, true)
        val sources = mutableStateOf(listOf(source))
        val state = mutableStateOf(HomeUiState(selectedFeed = HomeFeedType.ForYou, videos = listOf(video)))
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            HomeScreen(home = state.value, sources = sources.value,
                onFeedSelected = { state.value = state.value.copy(selectedFeed = it) },
                onRefresh = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
        } }
        rule.onNodeWithTag("home-feed-foryou").assertIsSelected()
        rule.runOnIdle { sources.value = listOf(source.copy(isEnabled = false)) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(HomeFeedType.ForYou, state.value.selectedFeed) }
        rule.onNodeWithTag("home-feed-shorts").assertDoesNotExist()
        rule.onNodeWithTag("home-feed-trending").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(HomeFeedType.Trending, state.value.selectedFeed) }
        rule.runOnIdle { sources.value = listOf(source) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(HomeFeedType.Trending, state.value.selectedFeed) }
    }

    @Test fun commentDragKeepsMovingAndMetadataOrPagingDoNotResetItsPosition() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val item = mutableStateOf(video)
        val comments = mutableStateOf((0..39).map { index ->
            VideoCommentUiModel("c$index", "Author $index", message = "Comment $index. ".repeat(12), age = "Today", replyCount = 3)
        })
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            VideoDetailScreen(
                video = item.value, player = player, playback = PlaybackUiState(),
                nowPlaying = NowPlayingUiState(video = item.value, comments = comments.value), queueVideos = emptyList(),
                onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {},
                onToggleWatchLater = {}, onToggleFollowing = {}, onSeek = {}, onSpeedChange = {},
                onQualityChange = {}, onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {},
                onRetryPlayback = {}, onVideoClick = {}, onVideoLongClick = {}, creatorChannel = null,
                onCreatorPreview = {}, onCreatorClick = {}, onFullscreen = {}, renderPlayer = false,
            )
        } }
        val list = rule.onNodeWithTag("video-detail-list")
        list.performScrollToNode(hasTestTag("now-playing-section-comments"))
        rule.onNodeWithTag("now-playing-section-comments").performClick()
        list.performScrollToNode(hasTestTag("comment-c5"))
        fun scroll() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = scroll()
        list.performTouchInput { swipe(center, center - Offset(0f, 240f), durationMillis = 600) }
        rule.waitForIdle()
        val afterDrag = scroll()
        assertTrue("Dragging a comment body should scroll", afterDrag > before)
        rule.runOnIdle {
            item.value = item.value.copy(title = "Resolved video title")
            comments.value = comments.value + VideoCommentUiModel("last", "Last", message = "Next page", age = "Now")
        }
        assertEquals("Metadata/page arrival must preserve the scroll anchor", afterDrag, scroll(), 1f)
        list.performTouchInput { swipe(center, center + Offset(0f, 180f), durationMillis = 600) }
        rule.waitForIdle()
        assertTrue("Reverse drag should not jump to the start", scroll() > 0f && scroll() < afterDrag)
    }

    @Test fun visiblePlaylistReflectsAnAppendedLastVideo() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val playlist = PlaylistUiModel("auto", "Played fixture", "", listOf(video.id), isPlaybackQueue = true)
        val state = mutableStateOf(GrayjayUiState(
            videos = listOf(video), libraryVideos = listOf(video), playlists = listOf(playlist),
            playbackPlaylist = playlist,
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Playlist, playlist.id),
        ))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        val last = video.copy(id = "last-added", title = "Last added")
        rule.runOnIdle { state.value = state.value.copy(
            videos = listOf(video, last), libraryVideos = listOf(video, last),
            playlists = listOf(playlist.copy(videoIds = listOf(video.id, last.id), videoCount = 2)),
        ) }
        rule.onNodeWithTag("playlist-detail-auto").performScrollToNode(hasTestTag("video-card-${last.id}"))
        rule.onNodeWithTag("video-card-${last.id}").assertIsDisplayed()
    }
}
