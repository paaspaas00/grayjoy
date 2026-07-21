package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.RemotePlaylistDetailUiState
import com.futo.platformplayer.compose.ui.VideoUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePlaylistDetailScreen(
    detail: RemotePlaylistDetailUiState,
    downloads: Map<String, DownloadUiModel>,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onPlayAll: () -> Unit,
    onDownloadAll: (DownloadMediaType) -> Unit,
    onCreateLocalPlaylist: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val playlist = detail.playlist ?: return
    val listState = rememberLazyListState()
    var showCreateSheet by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var localTitle by rememberSaveable(playlist.id) { mutableStateOf(playlist.title) }
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = detail.hasMore && !detail.isLoading && !detail.isLoadingMore && !detail.isLoadingAll,
        onLoadMore = onLoadMore,
    )
    val downloadableVideos = detail.videos.filterNot(VideoUiModel::isLive)
    val audioDownloaded = downloadableVideos.count {
        downloads[it.id]?.isComplete(DownloadMediaType.Audio) == true
    }
    val videoDownloaded = downloadableVideos.count {
        downloads[it.id]?.isComplete(DownloadMediaType.Video) == true
    }
    val displayedVideoCount = playlist.videoCount.takeIf { it > 0 } ?: detail.videos.size

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("remote-playlist-${playlist.id}"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.source_playlist), style = MaterialTheme.typography.labelLarge)
                    Text(playlist.title, style = MaterialTheme.typography.headlineMedium)
                    if (playlist.description.isNotBlank()) {
                        Text(playlist.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onDownloadAll(DownloadMediaType.Audio) },
                            enabled = downloadableVideos.isNotEmpty() && !detail.isLoadingAll,
                            modifier = Modifier.weight(1f).testTag("remote-playlist-download-audio"),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Text(
                                if (audioDownloaded > 0) {
                                    "${stringResource(R.string.download_all_audio)} $audioDownloaded/${downloadableVideos.size}"
                                } else stringResource(R.string.download_all_audio),
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = { onDownloadAll(DownloadMediaType.Video) },
                            enabled = downloadableVideos.isNotEmpty() && !detail.isLoadingAll,
                            modifier = Modifier.weight(1f).testTag("remote-playlist-download-video"),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Text(
                                if (videoDownloaded > 0) {
                                    "${stringResource(R.string.download_all_video)} $videoDownloaded/${downloadableVideos.size}"
                                } else stringResource(R.string.download_all_video),
                                maxLines = 1,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showCreateSheet = true },
                        enabled = detail.videos.isNotEmpty() && !detail.isLoadingAll,
                        modifier = Modifier.fillMaxWidth().testTag("remote-playlist-create-local"),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
                        Text(stringResource(R.string.make_local_playlist))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.video_count,
                                displayedVideoCount,
                                displayedVideoCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onPlayAll,
                            enabled = detail.videos.isNotEmpty() && !detail.isLoadingAll,
                            modifier = Modifier.testTag("remote-playlist-play-all"),
                        ) {
                            if (detail.isLoadingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            }
                            Text(stringResource(R.string.play_all))
                        }
                    }
                }
            }
        }

        detail.errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        item { SectionHeading(stringResource(R.string.videos)) }
        if (detail.isLoading) {
            item { VideoListSkeleton(count = 5, modifier = Modifier.fillMaxWidth()) }
        } else {
            itemsIndexed(detail.videos, key = { _, video -> video.id }) { index, video ->
                VideoCard(
                    video = video,
                    index = index,
                    download = downloads[video.id],
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) },
                )
            }
        }
        if (detail.isLoadingMore || detail.isLoadingAll) {
            item { VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth()) }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.make_local_playlist), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.make_local_playlist_description))
                OutlinedTextField(
                    value = localTitle,
                    onValueChange = { localTitle = it.take(80) },
                    modifier = Modifier.fillMaxWidth().testTag("remote-playlist-local-name"),
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showCreateSheet = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            showCreateSheet = false
                            onCreateLocalPlaylist(localTitle)
                        },
                        enabled = localTitle.isNotBlank(),
                        modifier = Modifier.testTag("remote-playlist-confirm-local"),
                    ) {
                        Text(stringResource(R.string.create))
                    }
                }
            }
        }
    }
}
