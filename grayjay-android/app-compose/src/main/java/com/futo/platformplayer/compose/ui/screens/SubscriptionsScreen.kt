package com.futo.platformplayer.compose.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

internal fun videoBelongsToFollowedChannel(video: VideoUiModel, channelId: String): Boolean =
    sequenceOf(
        video.authorUrl,
        video.channelId,
        "${video.sourceId}:${video.creator}",
    ).any { it.isNotBlank() && it == channelId }

internal fun videosForFollowedCreators(
    videos: List<VideoUiModel>,
    followedCreatorIds: Set<String>,
): List<VideoUiModel> = videos.filter { video ->
    video.authorUrl in followedCreatorIds ||
        video.channelId in followedCreatorIds ||
        "${video.sourceId}:${video.creator}" in followedCreatorIds
}

internal fun uploadsFromChannel(
    videos: List<VideoUiModel>,
    channelId: String?,
): List<VideoUiModel> = if (channelId == null) {
    videos
} else {
    videos.filter { videoBelongsToFollowedChannel(it, channelId) }
}

internal fun managedFollowedChannels(
    channels: List<ChannelUiModel>,
    query: String,
    sourceId: String?,
    ascending: Boolean,
): List<ChannelUiModel> {
    val normalizedQuery = query.trim()
    val filtered = channels.filter { channel ->
        (sourceId == null || channel.sourceId == sourceId) &&
            (normalizedQuery.isEmpty() ||
                channel.name.contains(normalizedQuery, ignoreCase = true) ||
                channel.source.contains(normalizedQuery, ignoreCase = true))
    }
    return if (ascending) {
        filtered.sortedBy { it.name.lowercase() }
    } else {
        filtered.sortedByDescending { it.name.lowercase() }
    }
}

private data class FollowedSourceOption(
    val id: String,
    val label: String,
)

@Composable
fun SubscriptionsScreen(
    channels: List<ChannelUiModel>,
    videos: List<VideoUiModel>,
    followedCreatorIds: Set<String>,
    onFollowedChange: (String, Boolean) -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onQueueSelection: (List<String>) -> Unit,
    onChannelClick: (ChannelUiModel) -> Unit,
    completeFeedLoaded: Boolean = false,
    completeFeedLoading: Boolean = false,
    completeFeedCompleted: Int = 0,
    completeFeedTotal: Int = 0,
    completeFeedError: String? = null,
    onRequestCompleteFeed: () -> Unit = {},
) {
    val performance = rememberDevicePerformanceProfile()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isManaging by rememberSaveable { mutableStateOf(false) }
    var videoSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedUploadChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showUploadChannelFilter by rememberSaveable { mutableStateOf(false) }
    var manageQuery by rememberSaveable { mutableStateOf("") }
    var manageSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var manageAscending by rememberSaveable { mutableStateOf(true) }
    var manageAsGrid by rememberSaveable { mutableStateOf(false) }
    var showManageSourceFilter by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirmation by rememberSaveable { mutableStateOf(false) }
    val selectedChannelIds = remember { mutableStateListOf<String>() }
    val selectedVideoIds = remember { mutableStateListOf<String>() }
    val followedChannels = remember(channels, followedCreatorIds) {
        channels.filter { it.id in followedCreatorIds }
    }
    val followedVideos = remember(videos, followedCreatorIds) {
        videosForFollowedCreators(videos, followedCreatorIds).distinctBy(VideoUiModel::id)
    }
    val visibleUploadVideos = remember(followedVideos, selectedUploadChannelId) {
        uploadsFromChannel(followedVideos, selectedUploadChannelId)
    }
    val sourceOptions = remember(followedChannels) {
        followedChannels
            .filter { it.sourceId.isNotBlank() }
            .distinctBy(ChannelUiModel::sourceId)
            .map { FollowedSourceOption(it.sourceId, it.source.ifBlank { it.sourceId }) }
            .sortedBy { it.label.lowercase() }
    }
    val managedChannels = remember(
        followedChannels,
        manageQuery,
        manageSourceId,
        manageAscending,
    ) {
        managedFollowedChannels(
            followedChannels,
            manageQuery,
            manageSourceId,
            manageAscending,
        )
    }

    fun leaveVideoSelectionMode() {
        videoSelectionMode = false
        selectedVideoIds.clear()
    }

    fun leaveManagement() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        selectedChannelIds.clear()
        isManaging = false
    }

    LaunchedEffect(completeFeedLoaded, completeFeedLoading, completeFeedError) {
        if (!completeFeedLoaded && !completeFeedLoading && completeFeedError == null) {
            onRequestCompleteFeed()
        }
    }
    LaunchedEffect(followedChannels, selectedUploadChannelId, manageSourceId) {
        if (followedChannels.none { it.id == selectedUploadChannelId }) {
            selectedUploadChannelId = null
        }
        if (sourceOptions.none { it.id == manageSourceId }) manageSourceId = null
        selectedChannelIds.retainAll(followedChannels.mapTo(mutableSetOf(), ChannelUiModel::id))
    }
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    BackHandler(enabled = isManaging) { leaveManagement() }

    if (isManaging) {
        ManageSubscriptionsPane(
            channels = managedChannels,
            allFollowedChannels = followedChannels,
            sourceOptions = sourceOptions,
            query = manageQuery,
            onQueryChange = { manageQuery = it.take(100) },
            selectedSourceId = manageSourceId,
            onOpenSourceFilter = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                showManageSourceFilter = true
            },
            ascending = manageAscending,
            onToggleSort = { manageAscending = !manageAscending },
            asGrid = manageAsGrid,
            onToggleLayout = { manageAsGrid = !manageAsGrid },
            selectedChannelIds = selectedChannelIds,
            onChannelClick = { channel ->
                if (selectedChannelIds.isEmpty()) {
                    onChannelClick(channel)
                } else if (channel.id in selectedChannelIds) {
                    selectedChannelIds.remove(channel.id)
                } else {
                    selectedChannelIds.add(channel.id)
                }
            },
            onChannelLongClick = { channel ->
                if (channel.id !in selectedChannelIds) selectedChannelIds.add(channel.id)
            },
            onRemoveSelected = { showRemoveConfirmation = true },
            onDone = ::leaveManagement,
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = rememberContentListState(),
                contentPadding = PaddingValues(
                    start = if (performance.compactContent) 8.dp else 16.dp,
                    top = if (performance.compactContent) 8.dp else 16.dp,
                    end = if (performance.compactContent) 8.dp else 16.dp,
                    bottom = if (videoSelectionMode) 88.dp else 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (performance.compactContent) 8.dp else 16.dp,
                ),
            ) {
                item {
                    SectionHeading(
                        title = stringResource(R.string.your_creators),
                        action = stringResource(R.string.manage),
                        onAction = {
                            leaveVideoSelectionMode()
                            isManaging = true
                        },
                    )
                }
                item {
                    if (followedChannels.isEmpty()) {
                        Text(
                            stringResource(R.string.no_followed_creators),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            followedChannels.forEach { channel ->
                                ChannelAvatar(
                                    channel = channel,
                                    onClick = { onChannelClick(channel) },
                                )
                            }
                        }
                    }
                }
                item {
                    UploadsHeading(
                        selectedCount = selectedVideoIds.size,
                        selectionMode = videoSelectionMode,
                        selectedChannel = followedChannels.firstOrNull {
                            it.id == selectedUploadChannelId
                        },
                        onOpenFilter = { showUploadChannelFilter = true },
                        onCancelSelection = ::leaveVideoSelectionMode,
                    )
                }
                if (completeFeedLoading) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (completeFeedTotal > 0) {
                                Text(
                                    "$completeFeedCompleted/$completeFeedTotal",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            if (followedVideos.isEmpty()) {
                                VideoListSkeleton(count = 4, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                completeFeedError?.let { error ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                error,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = onRequestCompleteFeed) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                if (completeFeedLoaded && followedVideos.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.enable_source_subscription_updates),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (selectedUploadChannelId != null && visibleUploadVideos.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_uploads_from_selected_channel),
                            modifier = Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(
                    items = visibleUploadVideos,
                    key = { _, video -> video.id },
                    contentType = { _, _ -> "video" },
                ) { index, video ->
                    VideoCard(
                        video = video,
                        index = index + 1,
                        selected = video.id in selectedVideoIds,
                        selectionMode = videoSelectionMode,
                        showProgress = true,
                        onClick = {
                            if (videoSelectionMode) {
                                if (video.id in selectedVideoIds) selectedVideoIds.remove(video.id)
                                else selectedVideoIds.add(video.id)
                                if (selectedVideoIds.isEmpty()) leaveVideoSelectionMode()
                            } else {
                                onVideoClick(video)
                            }
                        },
                        onLongClick = {
                            videoSelectionMode = true
                            if (video.id !in selectedVideoIds) selectedVideoIds.add(video.id)
                        },
                    )
                }
            }
            if (videoSelectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .testTag("subscriptions-selection-bar"),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.selected_count,
                                selectedVideoIds.size,
                                selectedVideoIds.size,
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(
                            onClick = {
                                onQueueSelection(selectedVideoIds.toList())
                                leaveVideoSelectionMode()
                            },
                            enabled = selectedVideoIds.isNotEmpty(),
                            modifier = Modifier.testTag("subscriptions-enqueue"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.PlaylistPlay,
                                contentDescription = stringResource(R.string.enqueue),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUploadChannelFilter) {
        UploadChannelFilterDialog(
            channels = followedChannels,
            selectedChannelId = selectedUploadChannelId,
            onDismiss = { showUploadChannelFilter = false },
            onSelect = { channelId ->
                selectedUploadChannelId = channelId
                showUploadChannelFilter = false
                leaveVideoSelectionMode()
            },
        )
    }
    if (showManageSourceFilter) {
        ManageSourceFilterDialog(
            sources = sourceOptions,
            selectedSourceId = manageSourceId,
            onDismiss = { showManageSourceFilter = false },
            onSelect = { sourceId ->
                manageSourceId = sourceId
                showManageSourceFilter = false
            },
        )
    }
    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = { Text(stringResource(R.string.remove_subscriptions_title)) },
            text = { Text(stringResource(R.string.remove_subscriptions_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedChannelIds.toList().forEach { onFollowedChange(it, false) }
                        selectedChannelIds.clear()
                        showRemoveConfirmation = false
                    },
                    modifier = Modifier.testTag("confirm-remove-subscriptions"),
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun UploadsHeading(
    selectedCount: Int,
    selectionMode: Boolean,
    selectedChannel: ChannelUiModel?,
    onOpenFilter: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selectionMode) {
                pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount)
            } else {
                stringResource(R.string.latest_uploads)
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        if (selectionMode) {
            TextButton(onClick = onCancelSelection) { Text(stringResource(R.string.cancel)) }
        } else {
            FilterChip(
                selected = selectedChannel != null,
                onClick = onOpenFilter,
                modifier = Modifier
                    .widthIn(max = 210.dp)
                    .testTag("uploads-channel-filter"),
                leadingIcon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
                label = {
                    Text(
                        selectedChannel?.let {
                            stringResource(R.string.from_channel, it.name)
                        } ?: stringResource(R.string.from_all),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun ManageSubscriptionsPane(
    channels: List<ChannelUiModel>,
    allFollowedChannels: List<ChannelUiModel>,
    sourceOptions: List<FollowedSourceOption>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedSourceId: String?,
    onOpenSourceFilter: () -> Unit,
    ascending: Boolean,
    onToggleSort: () -> Unit,
    asGrid: Boolean,
    onToggleLayout: () -> Unit,
    selectedChannelIds: List<String>,
    onChannelClick: (ChannelUiModel) -> Unit,
    onChannelLongClick: (ChannelUiModel) -> Unit,
    onRemoveSelected: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeading(
            title = stringResource(R.string.manage_subscriptions),
            action = stringResource(R.string.done),
            onAction = onDone,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manage-subscriptions-search"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            } else null,
            label = { Text(stringResource(R.string.search_followed_channels)) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectedSourceLabel = sourceOptions.firstOrNull { it.id == selectedSourceId }?.label
            FilterChip(
                selected = selectedSourceId != null,
                onClick = onOpenSourceFilter,
                leadingIcon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
                label = { Text(selectedSourceLabel ?: stringResource(R.string.all_sources)) },
                modifier = Modifier.testTag("manage-subscriptions-source-filter"),
            )
            FilterChip(
                selected = !ascending,
                onClick = onToggleSort,
                leadingIcon = { Icon(Icons.Outlined.SortByAlpha, contentDescription = null) },
                label = { Text(if (ascending) "A–Z" else "Z–A") },
                modifier = Modifier.testTag("manage-subscriptions-sort"),
            )
            FilterChip(
                selected = asGrid,
                onClick = onToggleLayout,
                leadingIcon = {
                    Icon(
                        if (asGrid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        stringResource(
                            if (asGrid) R.string.show_as_list else R.string.show_as_grid,
                        ),
                    )
                },
                modifier = Modifier.testTag("manage-subscriptions-layout"),
            )
        }
        if (selectedChannelIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manage-subscriptions-selection-header"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.selected_count,
                            selectedChannelIds.size,
                            selectedChannelIds.size,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = onRemoveSelected,
                        modifier = Modifier.testTag("remove-selected-subscriptions"),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.long_press_subscription),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (allFollowedChannels.isEmpty()) {
                        stringResource(R.string.no_followed_creators)
                    } else {
                        stringResource(R.string.no_channels_match)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (asGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("manage-subscriptions-grid"),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(channels, key = ChannelUiModel::id) { channel ->
                    ChannelManagementGridItem(
                        channel = channel,
                        selected = channel.id in selectedChannelIds,
                        onClick = { onChannelClick(channel) },
                        onLongClick = { onChannelLongClick(channel) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("manage-subscriptions-list"),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(channels, key = ChannelUiModel::id) { channel ->
                    ChannelManagementRow(
                        channel = channel,
                        selected = channel.id in selectedChannelIds,
                        onClick = { onChannelClick(channel) },
                        onLongClick = { onChannelLongClick(channel) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelManagementRow(
    channel: ChannelUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("manage-subscription-${channel.id}"),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatarImage(
                name = channel.name,
                thumbnailUrl = channel.thumbnailUrl,
                modifier = Modifier.size(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    channel.source,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelManagementGridItem(
    channel: ChannelUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("manage-subscription-grid-${channel.id}"),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChannelAvatarImage(
                name = channel.name,
                thumbnailUrl = channel.thumbnailUrl,
                modifier = Modifier.size(68.dp),
            )
            Text(
                channel.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                channel.source,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ChannelAvatar(
    channel: ChannelUiModel,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(width = 84.dp, height = 116.dp)
            .testTag("channel-avatar-${channel.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChannelAvatarImage(
                name = channel.name,
                thumbnailUrl = channel.thumbnailUrl,
                modifier = Modifier.size(64.dp),
            )
            Text(
                channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                channel.source,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun UploadChannelFilterDialog(
    channels: List<ChannelUiModel>,
    selectedChannelId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var pendingChannelId by remember(selectedChannelId) {
        mutableStateOf(selectedChannelId)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_uploads_by_channel)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("uploads-filter-all-channels"),
                ) {
                    Text(stringResource(R.string.from_all_channels))
                }
                Text(
                    stringResource(R.string.select_one_channel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .testTag("uploads-channel-filter-list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        channels.sortedBy { it.name.lowercase() },
                        key = ChannelUiModel::id,
                    ) { channel ->
                        val selected = channel.id == pendingChannelId
                        ListItem(
                            headlineContent = {
                                Text(
                                    channel.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(channel.source, maxLines = 1)
                            },
                            leadingContent = {
                                ChannelAvatarImage(
                                    name = channel.name,
                                    thumbnailUrl = channel.thumbnailUrl,
                                    modifier = Modifier.size(48.dp),
                                )
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = selected,
                                    onClick = { pendingChannelId = channel.id },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingChannelId = channel.id }
                                .testTag("uploads-filter-channel-${channel.id}"),
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { pendingChannelId?.let(onSelect) },
                enabled = pendingChannelId != null,
                modifier = Modifier.testTag("confirm-uploads-channel-filter"),
            ) {
                Text(stringResource(R.string.select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageSourceFilterDialog(
    sources: List<FollowedSourceOption>,
    selectedSourceId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_by_source)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.all_sources)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onSelect(null) }, onLongClick = {}),
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = if (selectedSourceId == null) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    )
                }
                items(sources, key = FollowedSourceOption::id) { source ->
                    ListItem(
                        headlineContent = { Text(source.label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onSelect(source.id) }, onLongClick = {}),
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = if (source.id == selectedSourceId) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
