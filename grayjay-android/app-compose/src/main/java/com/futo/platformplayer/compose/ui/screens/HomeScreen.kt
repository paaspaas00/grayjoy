package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile
import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.HomeUiState
import com.futo.platformplayer.compose.ui.PcPlaybackUiModel
import com.futo.platformplayer.compose.ui.ReleaseUpdateUiModel
import com.futo.platformplayer.compose.ui.SourceAvailability
import com.futo.platformplayer.compose.ui.SourceFilterOptionUiModel
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.UpdateDownloadUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class HomeBrowseTab(
    val id: String,
    val sourceId: String,
    val groupId: String,
    val label: String,
    val options: List<SourceFilterOptionUiModel>,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    home: HomeUiState,
    sources: List<SourceUiModel> = emptyList(),
    onFeedSelected: (HomeFeedType) -> Unit,
    onBrowseTabSelected: (String, String) -> Unit = { _, _ -> },
    onBrowseOptionSelected: (String, String, String) -> Unit = { _, _, _ -> },
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    availableUpdate: ReleaseUpdateUiModel? = null,
    onInstallUpdate: (ReleaseUpdateUiModel) -> Unit = {},
    updateDownload: UpdateDownloadUiModel? = null,
    onCancelUpdateDownload: () -> Unit = {},
    onHydrateVideoMetadata: (String) -> Unit = {},
    pcPlayback: PcPlaybackUiModel? = null,
    onPlayFromComputer: (String) -> Unit = {},
    onToggleComputerPlayback: (String) -> Unit = {},
    onPreviousComputerPlayback: (String) -> Unit = {},
    onNextComputerPlayback: (String) -> Unit = {},
    onSeekComputerPlayback: (String, Long) -> Unit = { _, _ -> },
) {
    val performance = rememberDevicePerformanceProfile()
    val compact = compactUi()
    var updateDetailsVisible by rememberSaveable { mutableStateOf(false) }
    val youtubeEnabled = sources.any { source ->
        source.id.equals("youtube", ignoreCase = true) && source.isEnabled &&
            source.availability != SourceAvailability.MissingPlugin
    }
    val feeds = remember(youtubeEnabled) {
        HomeFeedType.entries.filter { feed ->
            feed != HomeFeedType.Shorts || youtubeEnabled
        }
    }
    val browseTabs = remember(sources) {
        sources.asSequence()
            .filter {
                it.isEnabled && it.availability != SourceAvailability.MissingPlugin
            }
            .flatMap { source ->
                source.filterGroups.asSequence()
                    .filter { "home" in it.scopes && it.options.isNotEmpty() }
                    .map { group ->
                        HomeBrowseTab(
                            id = "${source.id}:${group.id}",
                            sourceId = source.id,
                            groupId = group.id,
                            label = group.label,
                            options = group.options,
                        )
                    }
            }
            .toList()
    }
    val pageKeys = remember(feeds, browseTabs) {
        feeds.map { "feed:${it.name}" } + browseTabs.map { "browse:${it.id}" }
    }
    val selectedBrowsePage = browseTabs.indexOfFirst {
        it.sourceId == home.browseSourceId && it.groupId == home.browseGroupId
    }.takeIf { it >= 0 }?.plus(feeds.size)
    val pagerState = rememberPagerState(
        initialPage = selectedBrowsePage
            ?: feeds.indexOf(home.selectedFeed).coerceAtLeast(0),
        pageCount = pageKeys::size,
    )
    val coroutineScope = rememberCoroutineScope()
    // Retain visited page content while swiping. Substituting loading skeletons for the
    // outgoing page forced a complete lazy-list rebuild halfway through the gesture.
    val presentedPages = remember(sources) { mutableStateMapOf<HomeFeedType, HomeUiState>() }
    SideEffect {
        if (home.browseSourceId == null && !home.isLoading) {
            presentedPages[home.selectedFeed] = home
        }
    }
    val activeFeed by rememberUpdatedState(home.selectedFeed)
    val activeBrowseSourceId by rememberUpdatedState(home.browseSourceId)
    val activeBrowseGroupId by rememberUpdatedState(home.browseGroupId)

    LaunchedEffect(youtubeEnabled, home.selectedFeed) {
        if (!youtubeEnabled && home.selectedFeed == HomeFeedType.Shorts) {
            onFeedSelected(HomeFeedType.Subscriptions)
        }
    }

    LaunchedEffect(pagerState, browseTabs) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val browseTab = browseTabs.getOrNull(page - feeds.size)
                if (browseTab != null) {
                    if (
                        activeBrowseSourceId != browseTab.sourceId ||
                        activeBrowseGroupId != browseTab.groupId
                    ) {
                        onBrowseTabSelected(browseTab.sourceId, browseTab.groupId)
                    }
                } else {
                    feeds.getOrNull(page)
                        ?.takeIf { activeBrowseSourceId != null || it != activeFeed }
                        ?.let(onFeedSelected)
                }
            }
    }
    LaunchedEffect(home.selectedFeed, home.browseSourceId, home.browseGroupId, browseTabs) {
        val selectedPage = browseTabs.indexOfFirst {
            it.sourceId == home.browseSourceId && it.groupId == home.browseGroupId
        }.takeIf { it >= 0 }?.plus(feeds.size)
            ?: feeds.indexOf(home.selectedFeed)
        if (
            selectedPage >= 0 && selectedPage != pagerState.currentPage &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    Column(Modifier.fillMaxSize()) {
        availableUpdate?.let { update ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("update-available-banner"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.update_available),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.update_available_description, update.versionName),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FilledTonalButton(onClick = { updateDetailsVisible = true }) {
                        Text(stringResource(R.string.details))
                    }
                }
            }
        }
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
                .padding(
                    horizontal = if (compact) 12.dp else if (performance.compactContent) 8.dp else 16.dp,
                    vertical = if (compact) 0.dp else if (performance.compactContent) 4.dp else 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            feeds.forEachIndexed { page, feed ->
                val selected = home.browseSourceId == null && home.selectedFeed == feed
                val bringIntoViewRequester = remember(feed) { BringIntoViewRequester() }
                LaunchedEffect(selected) {
                    if (selected) bringIntoViewRequester.bringIntoView()
                }
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (home.browseSourceId != null || home.selectedFeed != feed) {
                            onFeedSelected(feed)
                        }
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    },
                    label = { Text(stringResource(feed.labelRes)) },
                    modifier = Modifier
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .testTag("home-feed-${feed.name.lowercase()}"),
                )
            }
            browseTabs.forEachIndexed { index, tab ->
                val page = feeds.size + index
                val selected = home.browseSourceId == tab.sourceId &&
                    home.browseGroupId == tab.groupId
                val bringIntoViewRequester = remember(tab.id) { BringIntoViewRequester() }
                LaunchedEffect(selected) {
                    if (selected) bringIntoViewRequester.bringIntoView()
                }
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (!selected || home.browseOptionValue != null) {
                            onBrowseTabSelected(tab.sourceId, tab.groupId)
                        }
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    },
                    label = { Text(tab.label) },
                    modifier = Modifier
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .testTag("home-browse-${tab.id}"),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("home-feed-pager"),
            beyondViewportPageCount = 0,
            key = { pageKeys[it] },
        ) { page ->
            val browseTab = browseTabs.getOrNull(page - feeds.size)
            if (
                browseTab != null &&
                home.browseSourceId == browseTab.sourceId &&
                home.browseGroupId == browseTab.groupId &&
                home.browseOptionValue == null
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home-browse-options"),
                    contentPadding = PaddingValues(
                        if (performance.compactContent) 8.dp else 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        if (performance.compactContent) 8.dp else 12.dp,
                    ),
                ) {
                    item {
                        Text(
                            text = browseTab.label,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    itemsIndexed(
                        browseTab.options,
                        key = { _, option -> option.id },
                        contentType = { _, _ -> "browse-option" },
                    ) { _, option ->
                        Card(
                            onClick = {
                                onBrowseOptionSelected(
                                    browseTab.sourceId,
                                    browseTab.groupId,
                                    option.value,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Text(
                                text = option.label,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                return@HorizontalPager
            }
            val feed = browseTab?.let { HomeFeedType.ForYou } ?: feeds[page]
            val isSelectedPage = if (browseTab != null) {
                home.browseSourceId == browseTab.sourceId &&
                    home.browseGroupId == browseTab.groupId &&
                    home.browseOptionValue != null
            } else {
                home.browseSourceId == null && home.selectedFeed == feed
            }
            val listState = rememberContentListState()
            val pageHome = if (isSelectedPage) home else {
                presentedPages[feed] ?: HomeUiState(selectedFeed = feed, isLoading = true)
            }
            val presentedVideoIds = remember(feed) { mutableSetOf<String>() }
            val latestHomeVideos by rememberUpdatedState(home.videos)
            RequestNextPageEffect(
                listState = listState,
                canLoadMore = isSelectedPage && home.hasMore &&
                    !home.isLoading && !home.isLoadingMore,
                onLoadMore = onLoadMore,
            )
            LaunchedEffect(listState, isSelectedPage) {
                if (!isSelectedPage) return@LaunchedEffect
                snapshotFlow { listState.isScrollInProgress }
                    .distinctUntilChanged()
                    .collectLatest { isScrolling ->
                        if (isScrolling) return@collectLatest
                        delay(450L)
                        val visibleVideoIds = listState.layoutInfo.visibleItemsInfo
                            .mapNotNull { it.key as? String }
                            .toSet()
                        latestHomeVideos.asSequence()
                            .filter { it.id in visibleVideoIds }
                            .filter { it.duration.isBlank() && !it.isLive }
                            .forEach { onHydrateVideoMetadata(it.id) }
                    }
            }
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
                    contentPadding = PaddingValues(if (compact) 12.dp else if (performance.compactContent) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        if (compact || performance.compactContent) 8.dp else 16.dp,
                    ),
                ) {
                    if (!compact || browseTab != null || (feed == HomeFeedType.Subscriptions &&
                            pageHome.subscriptionsTotal > 0 && pageHome.subscriptionsLoaded < pageHome.subscriptionsTotal)) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!compact || browseTab != null) Text(
                                text = browseTab?.let {
                                    pageHome.browseOptionLabel ?: it.label
                                } ?: when (feed) {
                                    HomeFeedType.Subscriptions ->
                                        stringResource(R.string.latest_from_subscriptions)
                                    HomeFeedType.Shorts -> stringResource(R.string.shorts)
                                    HomeFeedType.ForYou -> stringResource(R.string.feed_for_you)
                                    HomeFeedType.Trending -> stringResource(R.string.trending_now)
                                    HomeFeedType.Live -> stringResource(R.string.live_now)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (feed == HomeFeedType.Subscriptions && pageHome.subscriptionsTotal > 0) {
                                SubscriptionLoadProgress(
                                    completed = pageHome.subscriptionsLoaded,
                                    total = pageHome.subscriptionsTotal,
                                )
                            }
                        }
                    }
                    }

                    if (pageHome.isLoading && pageHome.videos.isEmpty()) {
                        item { VideoListSkeleton(count = 5, modifier = Modifier.fillMaxWidth()) }
                    } else if (pageHome.errorMessage != null && pageHome.videos.isEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    pageHome.errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Button(onClick = onRefresh) {
                                    Text(stringResource(R.string.try_again))
                                }
                            }
                        }
                    } else if (pageHome.videos.isEmpty()) {
                        item {
                            Text(
                                when (feed) {
                                    HomeFeedType.Subscriptions ->
                                        stringResource(R.string.home_empty_subscriptions)
                                    HomeFeedType.Shorts -> stringResource(R.string.home_empty_feed)
                                    HomeFeedType.Live -> stringResource(R.string.home_empty_live)
                                    else -> stringResource(R.string.home_empty_feed)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    if (pageHome.videos.isNotEmpty()) {
                        itemsIndexed(
                            pageHome.videos,
                            key = { _, video -> video.id },
                            contentType = { _, _ -> "video" },
                        ) { index, video ->
                            val animateEntrance = remember(video.id) {
                                presentedVideoIds.add(video.id)
                            }
                            VideoCard(
                                video = video,
                                index = index,
                                showProgress = feed == HomeFeedType.Subscriptions ||
                                    feed == HomeFeedType.Shorts,
                                animateEntrance = animateEntrance && !listState.isScrollInProgress,
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

    if (updateDetailsVisible && availableUpdate != null) {
        val activeDownload = updateDownload?.takeIf {
            it.versionName == availableUpdate.versionName
        }
        AlertDialog(
            onDismissRequest = {
                if (activeDownload == null) updateDetailsVisible = false
            },
            title = {
                Text(stringResource(R.string.update_available_description, availableUpdate.versionName))
            },
            text = {
                if (activeDownload != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.update_downloading))
                        val total = activeDownload.totalBytes
                        if (total != null && total > 0L) {
                            LinearProgressIndicator(
                                progress = {
                                    (activeDownload.downloadedBytes.toFloat() / total)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(
                                    R.string.update_download_progress,
                                    formatDownloadSize(activeDownload.downloadedBytes),
                                    formatDownloadSize(total),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                stringResource(
                                    R.string.update_downloaded_amount,
                                    formatDownloadSize(activeDownload.downloadedBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    ReleaseMarkdown(
                        markdown = availableUpdate.changelog.ifBlank {
                            stringResource(R.string.no_changelog)
                        },
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = {
                        if (activeDownload != null) onCancelUpdateDownload()
                        else updateDetailsVisible = false
                    },
                ) {
                    Text(
                        stringResource(
                            if (activeDownload != null) R.string.cancel_download
                            else R.string.dismiss,
                        ),
                    )
                }
            },
            confirmButton = {
                if (activeDownload == null) {
                    Button(
                        onClick = { onInstallUpdate(availableUpdate) },
                        enabled = availableUpdate.debugApkUrl != null,
                    ) {
                        Text(stringResource(R.string.install))
                    }
                }
            },
        )
    }
}

internal fun formatDownloadSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val mebibytes = safe / (1024.0 * 1024.0)
    return if (mebibytes >= 1.0) "%.1f MB".format(mebibytes)
    else "%.0f KB".format(safe / 1024.0)
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
