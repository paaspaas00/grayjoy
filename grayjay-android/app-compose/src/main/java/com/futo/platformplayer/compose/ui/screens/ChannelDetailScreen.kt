package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

@Composable
fun ChannelDetailScreen(
    channel: ChannelUiModel,
    videos: List<VideoUiModel>,
    isFollowing: Boolean,
    videosAreChannelScoped: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onFollowingChange: (Boolean) -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
) {
    var query by rememberSaveable(channel.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = query.isBlank() && hasMore && !isLoading && !isLoadingMore,
        onLoadMore = onLoadMore,
    )
    val uriHandler = LocalUriHandler.current
    val channelVideos = (if (videosAreChannelScoped) {
        videos
    } else {
        videos.filter { video -> video.channelId == channel.id || video.authorUrl == channel.id }
    })
        .distinctBy(VideoUiModel::id)
    val visibleVideos = channelVideos.filter { video ->
        query.isBlank() || listOf(
            video.title,
            video.description,
            video.metadata,
        ).any { it.contains(query.trim(), ignoreCase = true) }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChannelAvatarImage(
                        name = channel.name,
                        thumbnailUrl = channel.thumbnailUrl,
                        modifier = Modifier.size(96.dp),
                    )
                    Text(
                        channel.name,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "${channel.source} • ${channel.followerCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (channel.description.isNotBlank()) {
                        Text(
                            channel.description,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (channel.links.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            channel.links.entries.take(6).forEach { (label, url) ->
                                AssistChip(
                                    onClick = { runCatching { uriHandler.openUri(url) } },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Link, contentDescription = null)
                                    },
                                    label = { Text(label.ifBlank { stringResource(R.string.link) }) },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { onFollowingChange(!isFollowing) },
                        modifier = Modifier.testTag("channel-follow"),
                    ) {
                        Text(stringResource(if (isFollowing) R.string.following else R.string.follow))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("channel-video-search"),
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
                placeholder = { Text(stringResource(R.string.search_channel_videos)) },
            )
        }

        if (errorMessage != null) {
            item {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            SectionHeading(
                if (query.isBlank()) stringResource(R.string.videos_with_count, channelVideos.size)
                else stringResource(R.string.results_with_count, visibleVideos.size),
            )
        }
        if (isLoading) {
            item {
                androidx.compose.foundation.layout.Box(
                    Modifier.testTag("channel-loading"),
                ) {
                    VideoListSkeleton(count = 4, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        itemsIndexed(visibleVideos, key = { _, video -> video.id }) { index, video ->
            CompactVideoCard(
                video = video,
                index = index,
                onClick = { onVideoClick(video) },
                onLongClick = { onVideoLongClick(video) },
            )
        }
        if (isLoadingMore) {
            item { VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth()) }
        }
        if (!isLoading && visibleVideos.isEmpty()) {
            item {
                Text(
                    if (query.isBlank()) {
                        stringResource(R.string.source_returned_no_videos)
                    } else {
                        stringResource(R.string.no_channel_videos_match, query.trim())
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
