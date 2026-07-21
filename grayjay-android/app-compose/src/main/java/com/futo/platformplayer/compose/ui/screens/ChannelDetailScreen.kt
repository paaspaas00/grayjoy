package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.ChannelDetailUiState
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

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
) {
    var query by rememberSaveable(channel.id, detail.selectedTab) { mutableStateOf("") }
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
        ChannelContentTab.Playlists -> emptyList()
    }
    val visibleVideos = currentVideos.filter { video ->
        query.isBlank() || listOf(video.title, video.description, video.metadata)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    val tabs = channelTabsFor(detail)

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
                        )
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

        if (detail.selectedTab != ChannelContentTab.Playlists) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("channel-video-search"),
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
                detail.playlists.size
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
            items(detail.playlists, key = PlaylistUiModel::id) { playlist ->
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
}

internal fun channelTabsFor(detail: ChannelDetailUiState): List<ChannelContentTab> = buildList {
    add(ChannelContentTab.Videos)
    if (detail.supportsShorts) add(ChannelContentTab.Shorts)
    if (detail.supportsPlaylists) add(ChannelContentTab.Playlists)
}
