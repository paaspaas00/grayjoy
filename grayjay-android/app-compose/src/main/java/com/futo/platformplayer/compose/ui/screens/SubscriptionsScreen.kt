package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

internal fun videosForFollowedCreators(
    videos: List<VideoUiModel>,
    followedCreatorIds: Set<String>,
): List<VideoUiModel> = videos.filter { video ->
    setOf(
        video.authorUrl,
        video.channelId,
        "${video.sourceId}:${video.creator}",
    ).any { it.isNotBlank() && it in followedCreatorIds }
}

internal fun newVideoCountsByCreator(
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
): Map<String, Int> = channels.associate { channel ->
    channel.id to videos.asSequence()
        .filter { it.lastWatchedAt <= 0L && it.watchProgress <= 0f }
        .filter { video ->
            sequenceOf(
                video.authorUrl,
                video.channelId,
                "${video.sourceId}:${video.creator}",
            ).any { it.isNotBlank() && it == channel.id }
        }
        .distinctBy(VideoUiModel::id)
        .count()
}

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
) {
    val performance = rememberDevicePerformanceProfile()
    var isManaging by rememberSaveable { mutableStateOf(false) }
    var videoSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedChannelIds = remember { mutableStateListOf<String>() }
    val selectedVideoIds = remember { mutableStateListOf<String>() }
    val followedChannels = remember(channels, followedCreatorIds) {
        channels.filter { it.id in followedCreatorIds }
    }
    val followedVideos = remember(videos, followedCreatorIds) {
        videosForFollowedCreators(videos, followedCreatorIds)
    }
    val newVideoCounts = remember(followedVideos, followedChannels) {
        newVideoCountsByCreator(followedVideos, followedChannels)
    }
    fun leaveVideoSelectionMode() {
        videoSelectionMode = false
        selectedVideoIds.clear()
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                title = if (isManaging) {
                    if (selectedChannelIds.isEmpty()) stringResource(R.string.manage_subscriptions)
                    else pluralStringResource(
                        R.plurals.selected_count,
                        selectedChannelIds.size,
                        selectedChannelIds.size,
                    )
                } else {
                    stringResource(R.string.your_creators)
                },
                action = stringResource(if (isManaging) R.string.done else R.string.manage),
                onAction = {
                    isManaging = !isManaging
                    selectedChannelIds.clear()
                    leaveVideoSelectionMode()
                },
            )
        }
        if (isManaging) {
            item {
                if (selectedChannelIds.isEmpty()) {
                    Text(
                        stringResource(R.string.long_press_subscription),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                selectedChannelIds.clear()
                                selectedChannelIds.addAll(followedChannels.map(ChannelUiModel::id))
                            },
                        ) { Text(stringResource(R.string.select_all)) }
                        Button(
                            onClick = {
                                selectedChannelIds.toList().forEach { onFollowedChange(it, false) }
                                selectedChannelIds.clear()
                            },
                            modifier = Modifier.testTag("remove-selected-subscriptions"),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                            Text(stringResource(R.string.remove))
                        }
                    }
                }
            }
            items(followedChannels, key = ChannelUiModel::id) { channel ->
                ChannelManagementRow(
                    channel = channel,
                    selected = channel.id in selectedChannelIds,
                    onClick = {
                        if (selectedChannelIds.isEmpty()) {
                            onChannelClick(channel)
                        } else if (channel.id in selectedChannelIds) {
                            selectedChannelIds.remove(channel.id)
                        } else {
                            selectedChannelIds.add(channel.id)
                        }
                    },
                    onLongClick = {
                        if (channel.id !in selectedChannelIds) selectedChannelIds.add(channel.id)
                    },
                )
            }
        } else item {
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
                            newVideoCount = newVideoCounts[channel.id].orZero(),
                            onClick = { onChannelClick(channel) },
                        )
                    }
                }
            }
        }
        if (!isManaging) item {
            SectionHeading(
                title = if (videoSelectionMode) {
                    pluralStringResource(
                        R.plurals.selected_count,
                        selectedVideoIds.size,
                        selectedVideoIds.size,
                    )
                } else {
                    stringResource(R.string.latest_uploads)
                },
                action = stringResource(R.string.cancel).takeIf { videoSelectionMode },
                onAction = ::leaveVideoSelectionMode,
            )
        }
        if (!isManaging && followedVideos.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.enable_source_subscription_updates),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!isManaging) itemsIndexed(
            followedVideos,
            key = { _, video -> video.id },
            contentType = { _, _ -> "video" },
        ) { index, video ->
            VideoCard(
                video = video,
                index = index + 1,
                selected = video.id in selectedVideoIds,
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

@Composable
private fun ChannelAvatar(
    channel: ChannelUiModel,
    newVideoCount: Int,
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
            Box {
                Box(
                    modifier = Modifier.size(64.dp),
                ) {
                    ChannelAvatarImage(
                        name = channel.name,
                        thumbnailUrl = channel.thumbnailUrl,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (newVideoCount > 0) Badge(Modifier.align(Alignment.TopEnd)) {
                    Text(newVideoCount.coerceAtMost(99).let { if (newVideoCount > 99) "99+" else "$it" })
                }
            }
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

private fun Int?.orZero(): Int = this ?: 0
