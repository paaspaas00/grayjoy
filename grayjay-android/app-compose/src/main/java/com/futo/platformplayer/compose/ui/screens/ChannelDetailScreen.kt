package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.ChannelDetailUiState
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    channel: ChannelUiModel,
    detail: ChannelDetailUiState,
    isFollowing: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    onTabSelected: (ChannelContentTab) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    perChannelPlaybackSpeedEnabled: Boolean = true,
    channelPlaybackSpeed: Float? = null,
    defaultPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float?) -> Unit = {},
) {
    var query by rememberSaveable(channel.id, detail.selectedTab) { mutableStateOf("") }
    var showSpeedSheet by rememberSaveable(channel.id) { mutableStateOf(false) }
    var showSortSheet by rememberSaveable(channel.id, detail.selectedTab) { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(channel.id) { mutableStateOf(false) }
    var descriptionOverflows by remember(channel.id) { mutableStateOf(false) }
    var sortModeName by rememberSaveable(channel.id, detail.selectedTab) {
        mutableStateOf(
            if (detail.selectedTab == ChannelContentTab.Playlists) {
                ChannelSortMode.Name.name
            } else {
                ChannelSortMode.UploadDate.name
            },
        )
    }
    var sortAscending by rememberSaveable(channel.id, detail.selectedTab) {
        mutableStateOf(detail.selectedTab == ChannelContentTab.Playlists)
    }
    val sortMode = runCatching { ChannelSortMode.valueOf(sortModeName) }
        .getOrDefault(ChannelSortMode.UploadDate)
    val listState = rememberLazyListState()
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = query.isBlank() && detail.hasMore && !detail.isLoading && !detail.isLoadingMore,
        onLoadMore = onLoadMore,
    )
    val uriHandler = LocalUriHandler.current
    val displayedChannel = detail.channel ?: channel
    val currentVideos = when (detail.selectedTab) {
        ChannelContentTab.Videos -> detail.videos
        ChannelContentTab.Shorts -> detail.shorts
        ChannelContentTab.Live -> detail.liveStreams
        ChannelContentTab.Playlists -> emptyList()
    }
    val filteredVideos = currentVideos.filter { video ->
        query.isBlank() || listOf(video.title, video.description, video.metadata)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    val visibleVideos = sortChannelVideos(filteredVideos, sortMode, sortAscending)
    val visiblePlaylists = sortChannelPlaylists(detail.playlists, sortAscending)
    val tabs = channelTabsFor(detail)
    val searchField: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = modifier.testTag("channel-video-search"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.clear_video_search),
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    stringResource(R.string.search_channel_videos),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
    val sortChip: @Composable () -> Unit = {
        FilterChip(
            selected = showSortSheet,
            onClick = { showSortSheet = true },
            leadingIcon = {
                Icon(
                    if (sortAscending) Icons.Outlined.ArrowUpward
                    else Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                )
            },
            label = {
                Text(
                    stringResource(
                        when (sortMode) {
                            ChannelSortMode.UploadDate -> R.string.sort_upload_date
                            ChannelSortMode.Popularity -> R.string.sort_popularity
                            ChannelSortMode.Name -> R.string.sort_name
                        },
                    ),
                )
            },
            modifier = Modifier.testTag("channel-sort"),
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.testTag("channel-detail-${channel.id}"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChannelAvatarImage(
                        name = displayedChannel.name,
                        thumbnailUrl = displayedChannel.thumbnailUrl,
                        modifier = Modifier.size(96.dp),
                    )
                    Text(
                        displayedChannel.name,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "${displayedChannel.source} • ${displayedChannel.followerCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (displayedChannel.description.isNotBlank()) {
                        Text(
                            displayedChannel.description,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!descriptionExpanded) {
                                    descriptionOverflows = result.hasVisualOverflow
                                }
                            },
                        )
                        if (descriptionOverflows || descriptionExpanded) {
                            TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                                Text(
                                    stringResource(
                                        if (descriptionExpanded) R.string.show_less
                                        else R.string.show_more,
                                    ),
                                )
                            }
                        }
                    }
                    if (displayedChannel.links.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            displayedChannel.links.entries.take(6).forEach { (label, url) ->
                                AssistChip(
                                    onClick = { runCatching { uriHandler.openUri(url) } },
                                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                    label = { Text(label.ifBlank { stringResource(R.string.link) }) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onFollowingChange(!isFollowing) },
                            modifier = Modifier.testTag("channel-follow"),
                        ) {
                            Text(stringResource(if (isFollowing) R.string.following else R.string.follow))
                        }
                        if (perChannelPlaybackSpeedEnabled) {
                            Button(
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.testTag("channel-playback-speed"),
                            ) {
                                Icon(Icons.Outlined.Speed, contentDescription = null)
                                Text(
                                    channelPlaybackSpeed?.let(::formatChannelSpeed)
                                        ?: stringResource(R.string.default_speed_label),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { tab ->
                    FilterChip(
                        selected = detail.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        label = { Text(stringResource(tab.labelRes)) },
                        modifier = Modifier.testTag("channel-tab-${tab.name.lowercase()}"),
                    )
                }
            }
        }

        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (channelSearchUsesStackedLayout(maxWidth.value)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (detail.selectedTab != ChannelContentTab.Playlists) {
                            searchField(Modifier.fillMaxWidth())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            sortChip()
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (detail.selectedTab != ChannelContentTab.Playlists) {
                            searchField(Modifier.weight(1f))
                        }
                        sortChip()
                    }
                }
            }
        }

        detail.errorMessage?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            val count = if (detail.selectedTab == ChannelContentTab.Playlists) {
                visiblePlaylists.size
            } else if (query.isBlank()) {
                currentVideos.size
            } else {
                visibleVideos.size
            }
            SectionHeading(
                if (query.isNotBlank()) stringResource(R.string.results_with_count, count)
                else "${stringResource(detail.selectedTab.labelRes)} ($count)",
            )
        }

        if (detail.isLoading) {
            item {
                Box(Modifier.testTag("channel-loading")) {
                    if (detail.selectedTab == ChannelContentTab.Playlists) {
                        SuggestionListSkeleton(count = 4)
                    } else {
                        VideoListSkeleton(count = 4, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else if (detail.selectedTab == ChannelContentTab.Playlists) {
            items(visiblePlaylists, key = PlaylistUiModel::id) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
            }
        } else {
            itemsIndexed(visibleVideos, key = { _, video -> video.id }) { index, video ->
                CompactVideoCard(
                    video = video,
                    index = index,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) },
                )
            }
        }

        if (detail.isLoadingMore) {
            item {
                if (detail.selectedTab == ChannelContentTab.Playlists) {
                    SuggestionListSkeleton(count = 2)
                } else {
                    VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (!detail.isLoading && currentVideos.isEmpty() &&
            (detail.selectedTab != ChannelContentTab.Playlists || detail.playlists.isEmpty())
        ) {
            item {
                Text(
                    if (query.isBlank()) stringResource(R.string.source_returned_no_content)
                    else stringResource(R.string.no_channel_videos_match, query.trim()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.channel_playback_speed),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.channel_playback_speed_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = channelPlaybackSpeed == null,
                        onClick = {
                            onPlaybackSpeedChange(null)
                            showSpeedSheet = false
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.app_default_speed,
                                    formatChannelSpeed(defaultPlaybackSpeed),
                                ),
                            )
                        },
                    )
                    playbackSpeedChoices.forEach { speed ->
                        FilterChip(
                            selected = channelPlaybackSpeed == speed,
                            onClick = {
                                onPlaybackSpeedChange(speed)
                                showSpeedSheet = false
                            },
                            label = { Text(formatChannelSpeed(speed)) },
                        )
                    }
                }
                TextButton(
                    onClick = { showSpeedSheet = false },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.sort), style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (detail.selectedTab == ChannelContentTab.Playlists) {
                        FilterChip(
                            selected = sortMode == ChannelSortMode.Name,
                            onClick = { sortModeName = ChannelSortMode.Name.name },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                            },
                            label = { Text(stringResource(R.string.sort_name)) },
                        )
                    } else {
                        FilterChip(
                            selected = sortMode == ChannelSortMode.UploadDate,
                            onClick = { sortModeName = ChannelSortMode.UploadDate.name },
                            label = { Text(stringResource(R.string.sort_upload_date)) },
                        )
                        if (detail.supportsPopularSort || currentVideos.any { it.viewCount > 0L }) {
                            FilterChip(
                                selected = sortMode == ChannelSortMode.Popularity,
                                onClick = { sortModeName = ChannelSortMode.Popularity.name },
                                label = { Text(stringResource(R.string.sort_popularity)) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sortAscending,
                        onClick = { sortAscending = true },
                        leadingIcon = {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
                        },
                        label = { Text(stringResource(R.string.ascending)) },
                    )
                    FilterChip(
                        selected = !sortAscending,
                        onClick = { sortAscending = false },
                        leadingIcon = {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                        },
                        label = { Text(stringResource(R.string.descending)) },
                    )
                }
                TextButton(
                    onClick = { showSortSheet = false },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

private val playbackSpeedChoices =
    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun formatChannelSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

internal fun channelTabsFor(detail: ChannelDetailUiState): List<ChannelContentTab> = buildList {
    add(ChannelContentTab.Videos)
    if (detail.supportsShorts) add(ChannelContentTab.Shorts)
    if (detail.liveContentType != null) add(ChannelContentTab.Live)
    if (detail.supportsPlaylists) add(ChannelContentTab.Playlists)
}

internal enum class ChannelSortMode { UploadDate, Popularity, Name }

internal fun channelSearchUsesStackedLayout(availableWidthDp: Float): Boolean =
    availableWidthDp < 520f

internal fun sortChannelVideos(
    videos: List<VideoUiModel>,
    mode: ChannelSortMode,
    ascending: Boolean,
): List<VideoUiModel> {
    val known = when (mode) {
        ChannelSortMode.UploadDate -> videos.filter { it.publishedAtMs > 0L }
        ChannelSortMode.Popularity -> videos.filter { it.viewCount > 0L }
        ChannelSortMode.Name -> videos
    }
    val unknownIds = known.mapTo(mutableSetOf(), VideoUiModel::id)
    val sorted = when (mode) {
        ChannelSortMode.UploadDate -> known.sortedBy(VideoUiModel::publishedAtMs)
        ChannelSortMode.Popularity -> known.sortedBy(VideoUiModel::viewCount)
        ChannelSortMode.Name -> known.sortedBy { it.title.lowercase() }
    }.let { if (ascending) it else it.asReversed() }
    return sorted + videos.filterNot { it.id in unknownIds }
}

internal fun sortChannelPlaylists(
    playlists: List<PlaylistUiModel>,
    ascending: Boolean,
): List<PlaylistUiModel> = playlists
    .sortedBy { it.title.lowercase() }
    .let { if (ascending) it else it.asReversed() }
