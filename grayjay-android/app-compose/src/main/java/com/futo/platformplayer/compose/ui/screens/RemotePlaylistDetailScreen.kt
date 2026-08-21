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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.data.playlistTitleExists
import com.futo.platformplayer.compose.data.uniqueRemotePlaylistTitle
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.RemotePlaylistDetailUiState
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.supportsOfflineDownload

internal enum class RemotePlaylistSortMode { PlaylistOrder, Popularity, UploadDate }

internal fun sortedRemotePlaylistVideos(
    videos: List<VideoUiModel>,
    mode: RemotePlaylistSortMode,
    ascending: Boolean,
): List<VideoUiModel> {
    if (mode == RemotePlaylistSortMode.PlaylistOrder) {
        return if (ascending) videos else videos.asReversed()
    }
    val key: (VideoUiModel) -> Long = when (mode) {
        RemotePlaylistSortMode.Popularity -> VideoUiModel::viewCount
        RemotePlaylistSortMode.UploadDate -> VideoUiModel::publishedAtMs
        RemotePlaylistSortMode.PlaylistOrder -> error("Handled above")
    }
    val (known, unknown) = videos.withIndex().partition { key(it.value) > 0L }
    val sortedKnown = known.sortedWith(compareBy<IndexedValue<VideoUiModel>> { key(it.value) }.thenBy { it.index })
    return (if (ascending) sortedKnown else sortedKnown.asReversed()).map { it.value } +
        unknown.map { it.value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePlaylistDetailScreen(
    detail: RemotePlaylistDetailUiState,
    downloads: Map<String, DownloadUiModel>,
    localPlaylists: List<PlaylistUiModel> = emptyList(),
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onPlayAll: () -> Unit,
    onDownloadAll: (DownloadMediaType) -> Unit,
    onCancelDownloadAll: (DownloadMediaType) -> Unit = {},
    onCreateLocalPlaylist: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val playlist = detail.playlist ?: return
    val listState = rememberLazyListState()
    var showCreateSheet by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showSortSheet by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var sortModeName by rememberSaveable(playlist.id) {
        mutableStateOf(RemotePlaylistSortMode.PlaylistOrder.name)
    }
    var sortAscending by rememberSaveable(playlist.id) { mutableStateOf(true) }
    val sortMode = RemotePlaylistSortMode.valueOf(sortModeName)
    val sortedVideos = remember(detail.videos, sortMode, sortAscending) {
        sortedRemotePlaylistVideos(detail.videos, sortMode, sortAscending)
    }
    val existingPlaylistTitles = localPlaylists.map(PlaylistUiModel::title)
    val suggestedLocalTitle = uniqueRemotePlaylistTitle(
        requestedTitle = playlist.title,
        channelName = detail.videos.firstOrNull()?.creator.orEmpty(),
        existingTitles = existingPlaylistTitles,
        fallbackTitle = stringResource(R.string.imported_playlist),
    )
    var localTitle by rememberSaveable(playlist.id, suggestedLocalTitle) {
        mutableStateOf(suggestedLocalTitle)
    }
    val duplicateLocalTitle = playlistTitleExists(localTitle, existingPlaylistTitles)
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = detail.hasMore && !detail.isLoading && !detail.isLoadingMore && !detail.isLoadingAll,
        onLoadMore = onLoadMore,
    )
    val downloadableVideos = detail.videos.filter(VideoUiModel::supportsOfflineDownload)
    val audioDownloaded = downloadableVideos.count {
        downloads[it.id]?.isComplete(DownloadMediaType.Audio) == true
    }
    val videoDownloaded = downloadableVideos.count {
        downloads[it.id]?.isComplete(DownloadMediaType.Video) == true
    }
    val displayedVideoCount = playlist.videoCount.takeIf { it > 0 } ?: detail.videos.size
    val audioBatchActive = DownloadMediaType.Audio in detail.activeDownloadMediaTypes
    val videoBatchActive = DownloadMediaType.Video in detail.activeDownloadMediaTypes

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
                            onClick = {
                                if (audioBatchActive) onCancelDownloadAll(DownloadMediaType.Audio)
                                else onDownloadAll(DownloadMediaType.Audio)
                            },
                            enabled = audioBatchActive ||
                                (downloadableVideos.isNotEmpty() && !detail.isLoadingAll),
                            modifier = Modifier.weight(1f).testTag("remote-playlist-download-audio"),
                        ) {
                            Icon(
                                if (audioBatchActive) Icons.Outlined.Close
                                else Icons.Outlined.Download,
                                contentDescription = null,
                            )
                            Text(
                                if (audioBatchActive) {
                                    stringResource(R.string.cancel)
                                } else if (audioDownloaded > 0) {
                                    "${stringResource(R.string.download_all_audio)} $audioDownloaded/${downloadableVideos.size}"
                                } else stringResource(R.string.download_all_audio),
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (videoBatchActive) onCancelDownloadAll(DownloadMediaType.Video)
                                else onDownloadAll(DownloadMediaType.Video)
                            },
                            enabled = videoBatchActive ||
                                (downloadableVideos.isNotEmpty() && !detail.isLoadingAll),
                            modifier = Modifier.weight(1f).testTag("remote-playlist-download-video"),
                        ) {
                            Icon(
                                if (videoBatchActive) Icons.Outlined.Close
                                else Icons.Outlined.Download,
                                contentDescription = null,
                            )
                            Text(
                                if (videoBatchActive) {
                                    stringResource(R.string.cancel)
                                } else if (videoDownloaded > 0) {
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
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.videos), style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { showSortSheet = true }) {
                    Icon(Icons.Outlined.Sort, contentDescription = null)
                    Text(stringResource(R.string.sort))
                }
            }
        }
        if (detail.isLoading) {
            item { VideoListSkeleton(count = 5, modifier = Modifier.fillMaxWidth()) }
        } else {
            itemsIndexed(
                sortedVideos,
                key = { _, video -> video.id },
                contentType = { _, _ -> "video" },
            ) { index, video ->
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

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.sort_videos), style = MaterialTheme.typography.titleLarge)
                RemotePlaylistSortMode.entries.forEach { option ->
                    val label = when (option) {
                        RemotePlaylistSortMode.PlaylistOrder -> R.string.playlist_order
                        RemotePlaylistSortMode.Popularity -> R.string.popularity
                        RemotePlaylistSortMode.UploadDate -> R.string.upload_date
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = sortMode == option,
                            onClick = { sortModeName = option.name },
                        )
                        Text(stringResource(label))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sortAscending, onClick = { sortAscending = true })
                    Text(stringResource(R.string.ascending))
                    RadioButton(selected = !sortAscending, onClick = { sortAscending = false })
                    Text(stringResource(R.string.descending))
                }
                Button(
                    onClick = { showSortSheet = false },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.done)) }
            }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
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
                    isError = duplicateLocalTitle,
                    supportingText = if (duplicateLocalTitle) {
                        { Text(stringResource(R.string.playlist_name_already_exists)) }
                    } else {
                        null
                    },
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
                        enabled = localTitle.isNotBlank() && !duplicateLocalTitle,
                        modifier = Modifier.testTag("remote-playlist-confirm-local"),
                    ) {
                        Text(stringResource(R.string.create))
                    }
                }
            }
        }
    }
}
