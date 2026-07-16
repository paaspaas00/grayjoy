package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.HomeUiState
import com.futo.platformplayer.compose.ui.VideoUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    home: HomeUiState,
    onFeedSelected: (HomeFeedType) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
) {
    val listState = rememberLazyListState()
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = home.hasMore && !home.isLoading && !home.isLoadingMore,
        onLoadMore = onLoadMore,
    )
    PullToRefreshBox(
        isRefreshing = home.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-pull-to-refresh"),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeFeedType.entries.forEach { feed ->
                        FilterChip(
                            selected = home.selectedFeed == feed,
                            onClick = { onFeedSelected(feed) },
                            label = { Text(stringResource(feed.labelRes)) },
                            modifier = Modifier.testTag("home-feed-${feed.name.lowercase()}"),
                        )
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (home.selectedFeed) {
                            HomeFeedType.Subscriptions -> stringResource(R.string.latest_from_subscriptions)
                            HomeFeedType.ForYou -> stringResource(R.string.feed_for_you)
                            HomeFeedType.Trending -> stringResource(R.string.trending_now)
                            HomeFeedType.Live -> stringResource(R.string.live_now)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (
                        home.selectedFeed == HomeFeedType.Subscriptions &&
                        home.subscriptionsTotal > 0
                    ) {
                        SubscriptionLoadProgress(
                            completed = home.subscriptionsLoaded,
                            total = home.subscriptionsTotal,
                        )
                    }
                }
            }

            if (home.isLoading) {
                item {
                    VideoListSkeleton(
                        count = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (home.errorMessage != null && home.videos.isEmpty()) {
                item {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            home.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onRefresh) { Text(stringResource(R.string.try_again)) }
                    }
                }
            } else if (home.videos.isEmpty()) {
                item {
                    Text(
                        when (home.selectedFeed) {
                            HomeFeedType.Subscriptions ->
                                stringResource(R.string.home_empty_subscriptions)
                            HomeFeedType.Live ->
                                stringResource(R.string.home_empty_live)
                            else ->
                                stringResource(R.string.home_empty_feed)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            itemsIndexed(home.videos, key = { _, video -> video.id }) { index, video ->
                VideoCard(
                    video = video,
                    index = index,
                    showProgress = home.selectedFeed == HomeFeedType.Subscriptions,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) },
                )
            }

            if (home.isLoadingMore) {
                item { VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth()) }
            }

            if (home.errorMessage != null && home.videos.isNotEmpty()) {
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

@Composable
private fun SubscriptionLoadProgress(completed: Int, total: Int) {
    Text(
        text = "${completed.coerceIn(0, total)}/$total",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.testTag("subscription-load-progress"),
    )
}
