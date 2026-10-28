package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.sourceCreatorFallback

import com.futo.platformplayer.compose.onboarding.helpAnchor

import android.content.Intent
import com.futo.platformplayer.compose.ui.PageBackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.Role
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
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockRule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelDetailScreen(
    channel: ChannelUiModel,
    detail: ChannelDetailUiState,
    isFollowing: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    onTabSelected: (ChannelContentTab) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    shuffledPlaylistIds: Set<String> = emptySet(),
    onPlaylistShuffleChange: (String, Boolean) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onLoadMoreSearch: () -> Unit = {},
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onAddSelectionToPlaylist: (List<String>) -> Unit = {},
    onPlaySelection: (List<String>) -> Unit = {},
    perChannelPlaybackSpeedEnabled: Boolean = true,
    channelPlaybackSpeed: Float? = null,
    defaultPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float?) -> Unit = {},
    sponsorBlockGlobalRule: SponsorBlockRule = SponsorBlockRule(),
    sponsorBlockOverride: SponsorBlockRule? = null,
    onSponsorBlockOverrideChange: (SponsorBlockRule?) -> Unit = {},
    onSearchModeChange: (Boolean) -> Unit = {},
    isPageVisible: Boolean = true,
) {
    val performance = rememberDevicePerformanceProfile()
    val compactLayout = compactUi()
    val query = detail.searchQuery
    var selectedVideoIds by remember(channel.id, detail.selectedTab, query) { mutableStateOf(emptyList<String>()) }
    val selectionMode = selectedVideoIds.isNotEmpty()
    PageBackHandler(enabled = selectionMode) { selectedVideoIds = emptyList() }
    var showSettingsSheet by rememberSaveable(channel.id) { mutableStateOf(false) }
    var showSortSheet by rememberSaveable(channel.id, detail.selectedTab) { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(channel.id) { mutableStateOf(false) }
    var descriptionOverflows by remember(channel.id) { mutableStateOf(false) }
    var searchMode by rememberSaveable(channel.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var sortModeName by rememberSaveable(channel.id, detail.selectedTab) {
        mutableStateOf(
            if (detail.selectedTab.isPlaylistTab) {
                ChannelSortMode.Name.name
            } else {
                ChannelSortMode.UploadDate.name
            },
        )
    }
    var sortAscending by rememberSaveable(channel.id, detail.selectedTab) {
        mutableStateOf(detail.selectedTab.isPlaylistTab)
    }
    val sortMode = runCatching { ChannelSortMode.valueOf(sortModeName) }
        .getOrDefault(ChannelSortMode.UploadDate)
    val listState = rememberContentListState()
    val openingFromSheet = LocalCreatorSheet.current?.request?.let {
        it.channel.id == channel.id && it.phase == CreatorSheetPhase.Opening
    } == true
    LaunchedEffect(openingFromSheet) {
        if (openingFromSheet) {
            searchMode = false
            listState.requestScrollToItem(0)
        }
    }
    fun onSearchFocusChanged(focused: Boolean) {
        if (focused && !detail.selectedTab.isPlaylistTab) {
            searchMode = true
            // Pin before the IME starts. A delayed animated scroll exposes the old field bounds
            // to Android's bring-into-view request and can pan the entire window offscreen.
            listState.requestScrollToItem(CHANNEL_SEARCH_FOCUSED_SCROLL_INDEX)
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
    val shareLabel = stringResource(R.string.share)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun revealChannel() {
        searchMode = false
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        scope.launch { listState.animateScrollToItem(0) }
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    PageBackHandler(enabled = searchMode && !selectionMode && isPageVisible) {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else revealChannel()
    }
    var sawKeyboard by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(searchMode, imeVisible, isPageVisible) {
        if (!isPageVisible) { sawKeyboard = false; return@LaunchedEffect }
        if (searchMode && imeVisible) sawKeyboard = true
        else if (searchMode && sawKeyboard && !imeVisible) {
            // Dismissing the IME is not navigation. Keep the pinned results and scroll position;
            // a later Back or a downward gesture on the search header reveals the channel.
            focusManager.clearFocus(force = true)
            sawKeyboard = false
        }
        if (!searchMode) sawKeyboard = false
    }
    LaunchedEffect(searchMode) { onSearchModeChange(searchMode) }
    DisposableEffect(channel.id) { onDispose { onSearchModeChange(false) } }
    LaunchedEffect(detail.selectedTab) { if (detail.selectedTab.isPlaylistTab && searchMode) revealChannel() }
    val revealBounds = remember(channel.id) { ChannelSearchRevealBounds() }
    val revealHeaderModifier = Modifier.pointerInput(searchMode) {
        if (!searchMode) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.y + revealBounds.listTop > maxOf(revealBounds.controlsBottom, revealBounds.headingBottom)) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val distance = change.position - down.position
                if (distance.y > viewConfiguration.touchSlop && distance.y > kotlin.math.abs(distance.x)) {
                    change.consume()
                    revealChannel()
                    break
                }
            }
        }
    }
    val displayedChannel = detail.channel ?: channel
    val currentVideos = remember(detail.selectedTab, detail.videos, detail.shorts, detail.liveStreams) {
        when (detail.selectedTab) {
            ChannelContentTab.Videos -> detail.videos
            ChannelContentTab.Shorts -> detail.shorts
            ChannelContentTab.Live -> detail.liveStreams
            ChannelContentTab.Playlists, ChannelContentTab.Releases -> emptyList()
        }.distinctBy(VideoUiModel::id)
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
    val visibleVideoIds = remember(visibleVideos) { visibleVideos.mapTo(hashSetOf(), VideoUiModel::id) }
    LaunchedEffect(visibleVideoIds, detail.isLoading, detail.isSearching) {
        if (!detail.isLoading && !detail.isSearching) {
            selectedVideoIds = selectedVideoIds.filter(visibleVideoIds::contains)
        }
    }
    val visiblePlaylists = remember(detail.playlists, detail.releases, detail.selectedTab, sortAscending) {
        sortChannelPlaylists(if (detail.selectedTab == ChannelContentTab.Releases) detail.releases else detail.playlists, sortAscending)
    }
    val tabs = channelTabsFor(detail)
    val searchField: @Composable (Modifier) -> Unit = { modifier ->
        if (compactLayout) CompactChannelSearchField(
            value = query,
            onValueChange = onSearchQueryChange,
            count = if (query.isBlank()) currentVideos.size else visibleVideos.size,
            modifier = modifier.onFocusChanged { onSearchFocusChanged(it.isFocused) }.testTag("channel-video-search"),
        ) else GrayjoySearchField(
            value = query,
            shape = SearchFieldShape,
            onValueChange = onSearchQueryChange,
            modifier = modifier
                .onFocusChanged { state -> onSearchFocusChanged(state.isFocused) }
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
        AdaptiveFilterChip(
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

    Column(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .then(revealHeaderModifier)
            .onGloballyPositioned { revealBounds.listTop = it.boundsInRoot().top }
            .testTag("channel-detail-${channel.id}"),
        contentPadding = if (compactLayout) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = if (searchMode) 0.dp else 4.dp)
            else androidx.compose.foundation.layout.PaddingValues(
                start = if (performance.compactContent) 8.dp else 16.dp,
                end = if (performance.compactContent) 8.dp else 16.dp,
                top = if (searchMode) 0.dp else if (performance.compactContent) 8.dp else 16.dp,
                bottom = if (performance.compactContent) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(
            if (compactLayout) 4.dp else if (performance.compactContent) 8.dp else 16.dp,
        ),
    ) {
        item(key = "channel-summary") {
            // Short/empty result lists cannot scroll a full summary out of the viewport.
            // Search mode therefore removes it while keeping the controls' item/key stable.
            if (!searchMode) {
            if (compactLayout) CompactChannelHeader(
                channel = displayedChannel,
                following = isFollowing,
                onToggleFollow = { onFollowingChange(!isFollowing) },
                onSettings = if (perChannelPlaybackSpeedEnabled || displayedChannel.sourceId.equals("youtube", true))
                    ({ showSettingsSheet = true }) else null,
                actions = buildList {
                    add(PlaylistMenuAction(stringResource(R.string.share), Icons.Outlined.Share, tag = "channel-share", onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, displayedChannel.name)
                            putExtra(Intent.EXTRA_TEXT, displayedChannel.id)
                        }, shareLabel))
                    }))
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
                    Box(Modifier.fillMaxWidth()) {
                    ChannelAvatarImage(
                        name = displayedChannel.name,
                        thumbnailUrl = displayedChannel.thumbnailUrl,
                        modifier = Modifier.align(Alignment.Center).creatorSharedElement(displayedChannel.id, "avatar")
                            .testTag("channel-header-avatar")
                            .size(if (performance.compactContent) 72.dp else 96.dp),
                    )
                    if (perChannelPlaybackSpeedEnabled || displayedChannel.sourceId.equals("youtube", true)) {
                        ChannelSettingsButton(onClick = { showSettingsSheet = true }, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                    }
                    SharedCreatorText(displayedChannel.id, "name", displayedChannel.name,
                        textAlign = TextAlign.Center,
                        style = if (performance.compactContent) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                    )
                    SharedCreatorText(displayedChannel.id, "metadata", "${displayedChannel.source} • ${sourceCreatorFallback(displayedChannel.followerCount)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (displayedChannel.description.isNotBlank() && (!compactLayout || descriptionExpanded)) {
                        SharedCreatorText(displayedChannel.id, "description", displayedChannel.description,
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
                                AdaptiveAssistChip(
                                    onClick = { runCatching { uriHandler.openUri(url) } },
                                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                    label = { Text(label.ifBlank { stringResource(R.string.link) }) },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AdaptiveButton(
                            onClick = { onFollowingChange(!isFollowing) },
                            modifier = Modifier.weight(1f).creatorSharedElement(displayedChannel.id, "follow")
                                .testTag("channel-follow").helpAnchor("channel-follow"),
                        ) {
                            Text(stringResource(if (isFollowing) R.string.following else R.string.follow))
                        }
                        AdaptiveButton(
                            onClick = {
                                val shareUrl = displayedChannel.id
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, displayedChannel.name)
                                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        },
                                        shareLabel,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("channel-share"),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = if (compactLayout) stringResource(R.string.share) else null)
                            if (!compactLayout) Text(stringResource(R.string.share))
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
                    .onGloballyPositioned { revealBounds.controlsBottom = it.boundsInRoot().bottom }
                    .testTag("channel-search-header")
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = if (compactLayout || searchMode) 0.dp else if (performance.compactContent) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 4.dp else 8.dp),
            ) {
                if (compactLayout) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tabs.forEach { tab ->
                                AdaptiveFilterChip(selected = detail.selectedTab == tab, onClick = { onTabSelected(tab) },
                                    label = { Text(stringResource(tab.labelRes)) }, modifier = Modifier.testTag("channel-tab-${tab.name.lowercase()}"))
                            }
                        }
                        IconButton(onClick = { showSortSheet = true }, modifier = Modifier.testTag("channel-sort")) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.sort))
                        }
                    }
                    if (!detail.selectedTab.isPlaylistTab) searchField(Modifier.fillMaxWidth())
                } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabs.forEach { tab ->
                        AdaptiveFilterChip(
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
                            if (!detail.selectedTab.isPlaylistTab) {
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
                            if (!detail.selectedTab.isPlaylistTab) {
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

        if (!compactLayout || detail.selectedTab.isPlaylistTab) item {
            val count = if (detail.selectedTab.isPlaylistTab) {
                visiblePlaylists.size
            } else if (query.isBlank()) {
                currentVideos.size
            } else {
                visibleVideos.size
            }
            Box(Modifier.onGloballyPositioned { revealBounds.headingBottom = it.boundsInRoot().bottom }
                .testTag("channel-result-heading")) { SectionHeading(
                if (query.isNotBlank()) stringResource(R.string.results_with_count, count)
                else "${stringResource(detail.selectedTab.labelRes)} ($count)",
            ) }
        }

        if (detail.isLoading || (detail.isSearching && visibleVideos.isEmpty())) {
            item {
                Box(Modifier.testTag("channel-loading")) {
                    if (detail.selectedTab.isPlaylistTab) {
                        SuggestionListSkeleton(count = 4)
                    } else {
                        VideoListSkeleton(count = 4, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else if (detail.selectedTab.isPlaylistTab) {
            items(
                items = visiblePlaylists,
                key = PlaylistUiModel::id,
                contentType = { "playlist" },
            ) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) },
                    shuffled = playlist.id in shuffledPlaylistIds,
                    onShuffleChange = { onPlaylistShuffleChange(playlist.id, it) })
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
                    selected = video.id in selectedVideoIds,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) selectedVideoIds = if (video.id in selectedVideoIds) selectedVideoIds - video.id else selectedVideoIds + video.id
                        else onVideoClick(video)
                    },
                    onLongClick = {
                        if (video.id !in selectedVideoIds) selectedVideoIds = selectedVideoIds + video.id
                    },
                )
            }
        }

        if (detail.isLoadingMore || detail.isSearchLoadingMore) {
            item {
                if (detail.selectedTab.isPlaylistTab) {
                    SuggestionListSkeleton(count = 2)
                } else {
                    VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (!detail.isLoading && !detail.isSearching &&
            (if (detail.selectedTab.isPlaylistTab) {
                visiblePlaylists.isEmpty()
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

    if (selectionMode) Surface(modifier = Modifier.fillMaxWidth().testTag("channel-selection-bar"), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(selectedVideoIds.size.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { selectedVideoIds = emptyList() }, modifier = Modifier.testTag("channel-selection-cancel")) {
                Text(stringResource(R.string.cancel))
            }
            IconButton(onClick = {
                selectedVideoIds.filter(visibleVideoIds::contains).takeIf(List<String>::isNotEmpty)
                    ?.let(onAddSelectionToPlaylist)
            }, modifier = Modifier.testTag("channel-selection-add")) {
                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist))
            }
            IconButton(onClick = {
                val ids = visibleVideos.filter { it.id in selectedVideoIds && it.isAvailable && it.scheduledStartAtMs <= System.currentTimeMillis() }.map { it.id }
                if (ids.isNotEmpty()) { onPlaySelection(ids); selectedVideoIds = emptyList() }
            }, modifier = Modifier.testTag("channel-selection-play")) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.play_selected))
            }
        }
    }
    }
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.channel_settings),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (perChannelPlaybackSpeedEnabled) {
                    Text(stringResource(R.string.channel_playback_speed), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.channel_playback_speed_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChannelSpeedPreference(channelPlaybackSpeed, defaultPlaybackSpeed, onPlaybackSpeedChange)
                }
                if (displayedChannel.sourceId.equals("youtube", true)) {
                    Text(stringResource(R.string.sponsorblock), style = MaterialTheme.typography.titleMedium)
                    SponsorBlockRuleEditor(
                        rule = sponsorBlockOverride ?: sponsorBlockGlobalRule,
                        inherited = sponsorBlockOverride == null,
                        inheritedRule = sponsorBlockGlobalRule,
                        onCustomize = { onSponsorBlockOverrideChange(sponsorBlockGlobalRule) },
                        onUseInherited = { onSponsorBlockOverrideChange(null) },
                        onRuleChange = onSponsorBlockOverrideChange,
                    )
                }
                TextButton(
                    onClick = { showSettingsSheet = false },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.close))
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
                    if (detail.selectedTab.isPlaylistTab) {
                        AdaptiveFilterChip(
                            selected = sortMode == ChannelSortMode.Name,
                            onClick = { sortModeName = ChannelSortMode.Name.name },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                            },
                            label = { Text(stringResource(R.string.sort_name)) },
                        )
                    } else {
                        AdaptiveFilterChip(
                            selected = sortMode == ChannelSortMode.UploadDate,
                            onClick = { sortModeName = ChannelSortMode.UploadDate.name },
                            label = { Text(stringResource(R.string.sort_upload_date)) },
                        )
                        if (detail.supportsPopularSort || currentVideos.any { it.viewCount > 0L }) {
                            AdaptiveFilterChip(
                                selected = sortMode == ChannelSortMode.Popularity,
                                onClick = { sortModeName = ChannelSortMode.Popularity.name },
                                label = { Text(stringResource(R.string.sort_popularity)) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdaptiveFilterChip(
                        selected = sortAscending,
                        onClick = { sortAscending = true },
                        leadingIcon = {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
                        },
                        label = { Text(stringResource(R.string.ascending)) },
                    )
                    AdaptiveFilterChip(
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

private class ChannelSearchRevealBounds {
    var listTop = 0f
    var controlsBottom = 0f
    var headingBottom = 0f
}

private val playbackSpeedChoices =
    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.channel_settings)
    Box(modifier) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.testTag("channel-settings")) {
            Icon(Icons.Outlined.Settings, contentDescription = label)
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSpeedPreference(value: Float?, defaultSpeed: Float, onChange: (Float?) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val defaultLabel = stringResource(R.string.app_default_speed, formatChannelSpeed(defaultSpeed))
    Surface(
        onClick = { showDialog = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("channel-speed-picker"),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value?.let(::formatChannelSpeed) ?: defaultLabel, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
    if (showDialog) {
        var pendingSpeed by rememberSaveable { mutableStateOf(value) }
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        var listHeight by remember { mutableIntStateOf(0) }
        LaunchedEffect(listHeight) {
            if (listHeight > 0) {
                val index = if (pendingSpeed == null) 0 else playbackSpeedChoices.indexOf(pendingSpeed) + 1
                val center = with(density) { (56.dp * index + 28.dp).roundToPx() }
                scroll.scrollTo((center - listHeight / 2).coerceAtLeast(0))
            }
        }
        BasicAlertDialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().testTag("channel-speed-dialog")) {
                Column {
                    Text(stringResource(R.string.channel_playback_speed),
                        Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp)
                        .onSizeChanged { listHeight = it.height }.verticalScroll(scroll).selectableGroup()) {
                        (listOf<Float?>(null) + playbackSpeedChoices).forEach { speed ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(selected = pendingSpeed == speed, role = Role.RadioButton,
                                        onClick = { pendingSpeed = speed })
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                    .testTag(if (speed == null) "channel-speed-default" else "channel-speed-$speed"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                RadioButton(selected = pendingSpeed == speed, onClick = null)
                                Text(speed?.let(::formatChannelSpeed) ?: defaultLabel,
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDialog = false }, modifier = Modifier.testTag("channel-speed-cancel")) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = { onChange(pendingSpeed); showDialog = false },
                            modifier = Modifier.testTag("channel-speed-confirm")) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

private fun formatChannelSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

internal fun channelTabsFor(detail: ChannelDetailUiState): List<ChannelContentTab> = buildList {
    add(ChannelContentTab.Videos)
    if (detail.supportsShorts) add(ChannelContentTab.Shorts)
    if (detail.liveContentType != null) add(ChannelContentTab.Live)
    if (detail.supportsPlaylists) add(ChannelContentTab.Playlists)
    if (detail.supportsReleases) add(ChannelContentTab.Releases)
}

internal enum class ChannelSortMode { UploadDate, Popularity, Name }

internal fun channelSearchUsesStackedLayout(availableWidthDp: Float): Boolean =
    availableWidthDp < 520f

internal fun sortChannelVideos(
    videos: List<VideoUiModel>,
    mode: ChannelSortMode,
    ascending: Boolean,
): List<VideoUiModel> {
    val unique = videos.distinctBy(VideoUiModel::id)
    val known = when (mode) {
        ChannelSortMode.UploadDate -> unique.filter { it.publishedAtMs > 0L }
        ChannelSortMode.Popularity -> unique.filter { it.viewCount > 0L }
        ChannelSortMode.Name -> unique
    }
    val unknownIds = known.mapTo(mutableSetOf(), VideoUiModel::id)
    val sorted = when (mode) {
        ChannelSortMode.UploadDate -> known.sortedBy(VideoUiModel::publishedAtMs)
        ChannelSortMode.Popularity -> known.sortedBy(VideoUiModel::viewCount)
        ChannelSortMode.Name -> known.sortedBy { it.title.lowercase() }
    }.let { if (ascending) it else it.asReversed() }
    return sorted + unique.filterNot { it.id in unknownIds }
}

internal fun sortChannelPlaylists(
    playlists: List<PlaylistUiModel>,
    ascending: Boolean,
): List<PlaylistUiModel> = playlists
    .distinctBy(PlaylistUiModel::id)
    .sortedBy { it.title.lowercase() }
    .let { if (ascending) it else it.asReversed() }
