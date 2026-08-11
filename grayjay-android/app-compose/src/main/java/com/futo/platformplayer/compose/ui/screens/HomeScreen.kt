package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.HomeUiState
import com.futo.platformplayer.compose.ui.PcPlaybackUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    home: HomeUiState,
    onFeedSelected: (HomeFeedType) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    pcPlayback: PcPlaybackUiModel? = null,
    onPlayFromComputer: (String) -> Unit = {},
    onToggleComputerPlayback: (String) -> Unit = {},
    onPreviousComputerPlayback: (String) -> Unit = {},
    onNextComputerPlayback: (String) -> Unit = {},
    onSeekComputerPlayback: (String, Long) -> Unit = { _, _ -> },
) {
    val feeds = HomeFeedType.entries
    val pagerState = rememberPagerState(
        initialPage = feeds.indexOf(home.selectedFeed).coerceAtLeast(0),
        pageCount = feeds::size,
    )
    val coroutineScope = rememberCoroutineScope()
    val activeFeed by rememberUpdatedState(home.selectedFeed)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                feeds.getOrNull(page)
                    ?.takeIf { it != activeFeed }
                    ?.let(onFeedSelected)
            }
    }
    LaunchedEffect(home.selectedFeed) {
        val selectedPage = feeds.indexOf(home.selectedFeed)
        if (
            selectedPage >= 0 && selectedPage != pagerState.currentPage &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    Column(Modifier.fillMaxSize()) {
        pcPlayback?.let { playback ->
            PcPlaybackBanner(
                playback = playback,
                onPlayHere = { onPlayFromComputer(playback.computerId) },
                onTogglePlayback = { onToggleComputerPlayback(playback.computerId) },
                onPrevious = { onPreviousComputerPlayback(playback.computerId) },
                onNext = { onNextComputerPlayback(playback.computerId) },
                onSeek = { positionMs ->
                    onSeekComputerPlayback(playback.computerId, positionMs)
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            feeds.forEachIndexed { page, feed ->
                FilterChip(
                    selected = home.selectedFeed == feed,
                    onClick = {
                        if (home.selectedFeed != feed) onFeedSelected(feed)
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    },
                    label = { Text(stringResource(feed.labelRes)) },
                    modifier = Modifier.testTag("home-feed-${feed.name.lowercase()}"),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("home-feed-pager"),
            beyondViewportPageCount = 1,
            key = { feeds[it].name },
        ) { page ->
            val feed = feeds[page]
            val isSelectedPage = home.selectedFeed == feed
            val listState = rememberLazyListState()
            val presentedVideoIds = remember(feed) { mutableSetOf<String>() }
            RequestNextPageEffect(
                listState = listState,
                canLoadMore = isSelectedPage && home.hasMore &&
                    !home.isLoading && !home.isLoadingMore,
                onLoadMore = onLoadMore,
            )
            PullToRefreshBox(
                isRefreshing = isSelectedPage && home.isRefreshing,
                onRefresh = {
                    if (isSelectedPage) onRefresh()
                    else onFeedSelected(feed)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home-pull-to-refresh"),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (feed) {
                                    HomeFeedType.Subscriptions ->
                                        stringResource(R.string.latest_from_subscriptions)
                                    HomeFeedType.ForYou -> stringResource(R.string.feed_for_you)
                                    HomeFeedType.Trending -> stringResource(R.string.trending_now)
                                    HomeFeedType.Live -> stringResource(R.string.live_now)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (feed == HomeFeedType.Subscriptions && home.subscriptionsTotal > 0) {
                                SubscriptionLoadProgress(
                                    completed = home.subscriptionsLoaded,
                                    total = home.subscriptionsTotal,
                                )
                            }
                        }
                    }

                    if (!isSelectedPage || home.isLoading) {
                        item { VideoListSkeleton(count = 5, modifier = Modifier.fillMaxWidth()) }
                    } else if (home.errorMessage != null && home.videos.isEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    home.errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Button(onClick = onRefresh) {
                                    Text(stringResource(R.string.try_again))
                                }
                            }
                        }
                    } else if (home.videos.isEmpty()) {
                        item {
                            Text(
                                when (feed) {
                                    HomeFeedType.Subscriptions ->
                                        stringResource(R.string.home_empty_subscriptions)
                                    HomeFeedType.Live -> stringResource(R.string.home_empty_live)
                                    else -> stringResource(R.string.home_empty_feed)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    if (isSelectedPage) {
                        itemsIndexed(home.videos, key = { _, video -> video.id }) { index, video ->
                            val animateEntrance = remember(video.id) {
                                presentedVideoIds.add(video.id) && !listState.isScrollInProgress
                            }
                            VideoCard(
                                video = video,
                                index = index,
                                showProgress = feed == HomeFeedType.Subscriptions,
                                animateEntrance = animateEntrance,
                                onClick = { onVideoClick(video) },
                                onLongClick = { onVideoLongClick(video) },
                            )
                        }
                    }

                    if (isSelectedPage && home.isLoadingMore) {
                        item { VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth()) }
                    }

                    if (isSelectedPage && home.errorMessage != null && home.videos.isNotEmpty()) {
                        item {
                            Text(
                                home.errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionLoadProgress(completed: Int, total: Int) {
    Text(
        text = "${completed.coerceIn(0, total)}/$total",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.testTag("subscription-load-progress"),
    )
}
