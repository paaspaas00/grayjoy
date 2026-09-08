package com.futo.platformplayer.compose.ui.screens

import android.content.Intent
import com.futo.platformplayer.compose.ui.PageBackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile
import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.ChannelDetailUiState
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelDetailScreen(
    channel: ChannelUiModel,
    detail: ChannelDetailUiState,
    isFollowing: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    onTabSelected: (ChannelContentTab) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onLoadMore: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onLoadMoreSearch: () -> Unit = {},
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    perChannelPlaybackSpeedEnabled: Boolean = true,
    channelPlaybackSpeed: Float? = null,
    defaultPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float?) -> Unit = {},
) {
    val performance = rememberDevicePerformanceProfile()
    val compactLayout = compactUi()
    val query = detail.searchQuery
    var showSpeedSheet by rememberSaveable(channel.id) { mutableStateOf(false) }
    var showSortSheet by rememberSaveable(channel.id, detail.selectedTab) { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(channel.id) { mutableStateOf(false) }
    var descriptionOverflows by remember(channel.id) { mutableStateOf(false) }
    var searchFocused by remember(channel.id, detail.selectedTab) { mutableStateOf(false) }
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
    val listState = rememberContentListState()
    LaunchedEffect(searchFocused, channel.id, detail.selectedTab) {
        if (searchFocused && detail.selectedTab != ChannelContentTab.Playlists) {
            // Let the IME begin its inset animation, then keep the tabs and search controls at
            // the top while the large channel summary scrolls out of the available viewport.
            delay(120L)
            listState.animateScrollToItem(CHANNEL_SEARCH_FOCUSED_SCROLL_INDEX)
        }
    }
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = if (query.isBlank()) {
            detail.hasMore && !detail.isLoading && !detail.isLoadingMore
        } else {
            detail.searchIsRemote && detail.searchHasMore &&
                !detail.isSearching && !detail.isSearchLoadingMore
        },
        onLoadMore = if (query.isBlank()) onLoadMore else onLoadMoreSearch,
    )
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    PageBackHandler(enabled = searchFocused) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val searchHeaderScrollConnection = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    searchFocused
                ) {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                }
                return Offset.Zero
            }
        }
    }
    val displayedChannel = detail.channel ?: channel
    val currentVideos = when (detail.selectedTab) {
        ChannelContentTab.Videos -> detail.videos
        ChannelContentTab.Shorts -> detail.shorts
        ChannelContentTab.Live -> detail.liveStreams
        ChannelContentTab.Playlists -> emptyList()
    }
    val filteredVideos = remember(currentVideos, query) {
        val normalizedQuery = query.trim()
        currentVideos.filter { video ->
            normalizedQuery.isBlank() ||
                video.title.contains(normalizedQuery, ignoreCase = true) ||
                video.description.contains(normalizedQuery, ignoreCase = true) ||
                video.metadata.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val searchBaseVideos = if (query.isNotBlank() && detail.searchIsRemote) {
        detail.searchVideos
    } else {
        filteredVideos
    }
    val visibleVideos = remember(searchBaseVideos, sortMode, sortAscending) {
        sortChannelVideos(searchBaseVideos, sortMode, sortAscending)
    }
    val visiblePlaylists = remember(detail.playlists, sortAscending) {
        sortChannelPlaylists(detail.playlists, sortAscending)
    }
    val tabs = channelTabsFor(detail)
    val searchField: @Composable (Modifier) -> Unit = { modifier ->
        if (compactLayout) CompactChannelSearchField(
            value = query,
            onValueChange = onSearchQueryChange,
            count = if (query.isBlank()) currentVideos.size else visibleVideos.size,
            modifier = modifier.onFocusChanged { searchFocused = it.isFocused }.testTag("channel-video-search"),
        ) else OutlinedTextField(
            value = query,
            shape = SearchFieldShape,
            onValueChange = onSearchQueryChange,
            modifier = modifier
                .onFocusChanged { state -> searchFocused = state.isFocused }
                .testTag("channel-video-search"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
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
        modifier = Modifier
            .nestedScroll(searchHeaderScrollConnection)
            .testTag("channel-detail-${channel.id}"),
        contentPadding = if (compactLayout) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            else androidx.compose.foundation.layout.PaddingValues(if (performance.compactContent) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(
            if (compactLayout) 4.dp else if (performance.compactContent) 8.dp else 16.dp,
        ),
    ) {
        item {
            if (compactLayout) CompactChannelHeader(
                channel = displayedChannel,
                following = isFollowing,
                onToggleFollow = { onFollowingChange(!isFollowing) },
                actions = buildList {
                    add(PlaylistMenuAction(stringResource(R.string.share), Icons.Outlined.Share, tag = "channel-share", onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, displayedChannel.name)
                            putExtra(Intent.EXTRA_TEXT, displayedChannel.id)
                        }, context.getString(R.string.share)))
                    }))
                    if (perChannelPlaybackSpeedEnabled) add(PlaylistMenuAction(
                        stringResource(R.string.playback_speed), Icons.Outlined.Speed,
                        subtitle = channelPlaybackSpeed?.let(::formatChannelSpeed) ?: stringResource(R.string.default_speed_label),
                        tag = "channel-playback-speed", onClick = { showSpeedSheet = true },
                    ))
                    displayedChannel.links.entries.take(6).forEach { (label, url) ->
                        add(PlaylistMenuAction(label.ifBlank { stringResource(R.string.link) }, Icons.Outlined.Link,
                            tag = "channel-link-$url", onClick = { runCatching { uriHandler.openUri(url) } }))
                    }
                },
            ) else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        if (compactLayout || performance.compactContent) 12.dp else 20.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (compactLayout) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChannelAvatarImage(displayedChannel.name, displayedChannel.thumbnailUrl, modifier = Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(displayedChannel.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${displayedChannel.source} • ${displayedChannel.followerCount}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (displayedChannel.description.isNotBlank()) IconButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                                Icon(Icons.Outlined.Info, contentDescription = stringResource(if (descriptionExpanded) R.string.show_less else R.string.show_more))
                            }
                        }
                    } else {
                    ChannelAvatarImage(
                        name = displayedChannel.name,
                        thumbnailUrl = displayedChannel.thumbnailUrl,
                        modifier = Modifier.size(if (performance.compactContent) 72.dp else 96.dp),
                    )
                    Text(
                        displayedChannel.name,
                        textAlign = TextAlign.Center,
                        style = if (performance.compactContent) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                    )
                    Text(
                        "${displayedChannel.source} • ${displayedChannel.followerCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    }
                    if (displayedChannel.description.isNotBlank() && (!compactLayout || descriptionExpanded)) {
                        Text(
                            displayedChannel.description,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE
                            else if (performance.compactContent) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!descriptionExpanded) {
                                    descriptionOverflows = result.hasVisualOverflow
                                }
                            },
                        )
                        if (!compactLayout && (descriptionOverflows || descriptionExpanded)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onFollowingChange(!isFollowing) },
                            modifier = Modifier.testTag("channel-follow"),
                        ) {
                            Text(stringResource(if (isFollowing) R.string.following else R.string.follow))
                        }
                        Button(
                            onClick = {
                                val shareUrl = displayedChannel.id
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, displayedChannel.name)
                                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        },
                                        context.getString(R.string.share),
                                    ),
                                )
                            },
                            modifier = Modifier.testTag("channel-share"),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = if (compactLayout) stringResource(R.string.share) else null)
                            if (!compactLayout) Text(stringResource(R.string.share))
                        }
                        if (perChannelPlaybackSpeedEnabled) {
                            Button(
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.testTag("channel-playback-speed"),
                            ) {
                                Icon(Icons.Outlined.Speed, contentDescription = if (compactLayout) stringResource(R.string.playback_speed) else null)
                                if (!compactLayout) Text(
                                    channelPlaybackSpeed?.let(::formatChannelSpeed)
                                        ?: stringResource(R.string.default_speed_label),
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        stickyHeader(key = "channel-search-header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = if (compactLayout) 0.dp else if (performance.compactContent) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 4.dp else 8.dp),
            ) {
                if (compactLayout) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tabs.forEach { tab ->
                                FilterChip(selected = detail.selectedTab == tab, onClick = { onTabSelected(tab) },
                                    label = { Text(stringResource(tab.labelRes)) }, modifier = Modifier.testTag("channel-tab-${tab.name.lowercase()}"))
                            }
                        }
                        IconButton(onClick = { showSortSheet = true }, modifier = Modifier.testTag("channel-sort")) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.sort))
                        }
                    }
                    if (detail.selectedTab != ChannelContentTab.Playlists) searchField(Modifier.fillMaxWidth())
                } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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

        if (!compactLayout || detail.selectedTab == ChannelContentTab.Playlists) item {
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

        if (detail.isLoading || (detail.isSearching && visibleVideos.isEmpty())) {
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
            items(
                items = visiblePlaylists,
                key = PlaylistUiModel::id,
                contentType = { "playlist" },
            ) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
            }
        } else {
            itemsIndexed(
                visibleVideos,
                key = { _, video -> video.id },
                contentType = { _, _ -> "video" },
            ) { index, video ->
                CompactVideoCard(
                    video = video,
                    index = index,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) },
                )
            }
        }

        if (detail.isLoadingMore || detail.isSearchLoadingMore) {
            item {
                if (detail.selectedTab == ChannelContentTab.Playlists) {
                    SuggestionListSkeleton(count = 2)
                } else {
                    VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (!detail.isLoading && !detail.isSearching &&
            (if (detail.selectedTab == ChannelContentTab.Playlists) {
                detail.playlists.isEmpty()
            } else {
                visibleVideos.isEmpty()
            })
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

private const val CHANNEL_SEARCH_FOCUSED_SCROLL_INDEX = 1

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
