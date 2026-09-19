package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.futo.platformplayer.compose.ui.PageBackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.supportsOfflineDownload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistUiModel,
    videos: List<VideoUiModel>,
    downloads: Map<String, DownloadUiModel> = emptyMap(),
    activeDownloadMediaTypes: Set<DownloadMediaType> = emptySet(),
    automaticDownloadMediaTypes: Set<DownloadMediaType> = emptySet(),
    automaticDownloadsEnabled: Boolean = true,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onPlayAll: () -> Unit,
    onPlayFromHere: (String) -> Unit = {},
    onDownloadAllAsAudio: (List<String>) -> Unit = {},
    onDownloadAllAsVideo: (List<String>) -> Unit = {},
    onCancelDownloadAllAsAudio: () -> Unit = {},
    onCancelDownloadAllAsVideo: () -> Unit = {},
    onAutomaticDownloadChange: (DownloadMediaType, Boolean) -> Unit = { _, _ -> },
    onRename: (String) -> Unit = {},
    onAddSelectionToPlaylist: (List<String>) -> Unit = {},
    onRemoveVideos: (List<String>) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
    currentVideoId: String? = null,
    isPlaying: Boolean = false,
) {
    val compactLayout = compactUi()
    var showRenameDialog by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showReorderDialog by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showAutomaticDownloads by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var confirmRemoval by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var selectionMode by rememberSaveable(playlist.id) { mutableStateOf(false) }
    val selectedVideoIds = remember(playlist.id) { mutableStateListOf<String>() }
    val videosById = remember(videos) { videos.associateBy(VideoUiModel::id) }
    val playlistVideos = remember(playlist.videoIds, videosById) {
        playlist.videoIds.distinct().mapNotNull(videosById::get)
    }
    val downloadableIds = remember(playlistVideos) {
        playlistVideos.filter(VideoUiModel::supportsOfflineDownload).map(VideoUiModel::id)
    }
    val audioDownloadCount = remember(downloadableIds, downloads) {
        downloadableIds.count {
            downloads[it]?.isComplete(DownloadMediaType.Audio) == true
        }
    }
    val videoDownloadCount = remember(downloadableIds, downloads) {
        downloadableIds.count {
            downloads[it]?.isComplete(DownloadMediaType.Video) == true
        }
    }
    val audioBatchActive = DownloadMediaType.Audio in activeDownloadMediaTypes
    val videoBatchActive = DownloadMediaType.Video in activeDownloadMediaTypes

    fun leaveSelectionMode() {
        selectionMode = false
        selectedVideoIds.clear()
    }
    PageBackHandler(enabled = selectionMode) { leaveSelectionMode() }
    LaunchedEffect(playlistVideos) {
        selectedVideoIds.retainAll(playlistVideos.mapTo(mutableSetOf(), VideoUiModel::id))
        if (selectedVideoIds.isEmpty()) selectionMode = false
    }
    val listState = rememberContentListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("playlist-detail-${playlist.id}"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = if (compactLayout) 12.dp else 16.dp,
                top = if (compactLayout) 8.dp else 16.dp,
                end = if (compactLayout) 12.dp else 16.dp,
                bottom = if (selectionMode) 88.dp else 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 16.dp),
        ) {
            item {
                if (compactLayout) CompactPlaylistHeader(
                    title = playlist.title,
                    description = playlist.description,
                    playEnabled = playlistVideos.isNotEmpty(),
                    playTag = "playlist-play-all",
                    onPlayAll = onPlayAll,
                    actions = listOf(
                        PlaylistMenuAction(stringResource(R.string.download_all_audio), Icons.Outlined.Download,
                            enabled = audioBatchActive || downloadableIds.any { downloads[it]?.isComplete(DownloadMediaType.Audio) != true && downloads[it]?.isActive(DownloadMediaType.Audio) != true },
                            subtitle = if (audioBatchActive) stringResource(R.string.cancel_download) else null,
                            tag = "playlist-download-audio",
                            onClick = { if (audioBatchActive) onCancelDownloadAllAsAudio() else onDownloadAllAsAudio(downloadableIds) }),
                        PlaylistMenuAction(stringResource(R.string.download_all_video), Icons.Outlined.Download,
                            enabled = videoBatchActive || downloadableIds.any { downloads[it]?.isComplete(DownloadMediaType.Video) != true && downloads[it]?.isActive(DownloadMediaType.Video) != true },
                            subtitle = if (videoBatchActive) stringResource(R.string.cancel_download) else null,
                            tag = "playlist-download-video",
                            onClick = { if (videoBatchActive) onCancelDownloadAllAsVideo() else onDownloadAllAsVideo(downloadableIds) }),
                        PlaylistMenuAction(stringResource(R.string.rename_playlist), Icons.Outlined.Edit,
                            tag = "rename-current-playlist", onClick = { showRenameDialog = true }),
                        PlaylistMenuAction(
                            stringResource(R.string.automatic_downloads),
                            Icons.Outlined.Settings,
                            tag = "playlist-automatic-downloads",
                            onClick = { showAutomaticDownloads = true },
                        ),
                    ),
                ) else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(if (compactLayout) 16.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.local_playlist), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                playlist.title,
                                modifier = Modifier.weight(1f),
                                style = if (compactLayout) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                            )
                            IconButton(
                                onClick = { showAutomaticDownloads = true },
                                modifier = Modifier.testTag("playlist-automatic-downloads"),
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.automatic_downloads),
                                )
                            }
                            IconButton(
                                onClick = { showRenameDialog = true },
                                modifier = Modifier.testTag("rename-current-playlist"),
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.rename_playlist),
                                )
                            }
                        }
                        if (playlist.description.isNotBlank()) Text(playlist.description, style = MaterialTheme.typography.bodyLarge)
                        AdaptiveActionRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (audioBatchActive) onCancelDownloadAllAsAudio()
                                    else onDownloadAllAsAudio(downloadableIds)
                                },
                                enabled = audioBatchActive ||
                                    (downloadableIds.isNotEmpty() && downloadableIds.any {
                                        downloads[it]?.isComplete(DownloadMediaType.Audio) != true &&
                                            downloads[it]?.isActive(DownloadMediaType.Audio) != true
                                    }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("playlist-download-audio"),
                            ) {
                                Icon(
                                    if (audioBatchActive) Icons.Outlined.Close
                                    else Icons.Outlined.Download,
                                    contentDescription = null,
                                )
                                Text(
                                    if (audioBatchActive) {
                                        stringResource(R.string.cancel)
                                    } else if (audioDownloadCount > 0) {
                                        "${stringResource(R.string.download_all_audio)} $audioDownloadCount/${downloadableIds.size}"
                                    } else {
                                        stringResource(R.string.download_all_audio)
                                    },
                                    maxLines = 1,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (videoBatchActive) onCancelDownloadAllAsVideo()
                                    else onDownloadAllAsVideo(downloadableIds)
                                },
                                enabled = videoBatchActive ||
                                    (downloadableIds.isNotEmpty() && downloadableIds.any {
                                        downloads[it]?.isComplete(DownloadMediaType.Video) != true &&
                                            downloads[it]?.isActive(DownloadMediaType.Video) != true
                                    }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("playlist-download-video"),
                            ) {
                                Icon(
                                    if (videoBatchActive) Icons.Outlined.Close
                                    else Icons.Outlined.Download,
                                    contentDescription = null,
                                )
                                Text(
                                    if (videoBatchActive) {
                                        stringResource(R.string.cancel)
                                    } else if (videoDownloadCount > 0) {
                                        "${stringResource(R.string.download_all_video)} $videoDownloadCount/${downloadableIds.size}"
                                    } else {
                                        stringResource(R.string.download_all_video)
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                        AdaptiveActionRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.video_count,
                                    playlist.videoIds.size,
                                    playlist.videoIds.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = onPlayAll,
                                enabled = playlist.videoIds.isNotEmpty(),
                                modifier = Modifier.testTag("playlist-play-all"),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text(stringResource(R.string.play_all))
                            }
                        }
                    }
                }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (compactLayout && !selectionMode) pluralStringResource(R.plurals.video_count, playlistVideos.size, playlistVideos.size) else stringResource(R.string.videos),
                        style = if (compactLayout) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    )
                    if (selectionMode) {
                        Text(
                            if (compactLayout) selectedVideoIds.size.toString() else pluralStringResource(
                                R.plurals.selected_count,
                                selectedVideoIds.size,
                                selectedVideoIds.size,
                            ),
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (selectionMode) {
                        TextButton(onClick = ::leaveSelectionMode) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showReorderDialog = true },
                            enabled = playlistVideos.size > 1,
                            modifier = Modifier.testTag("playlist-open-reorder"),
                        ) {
                            Icon(
                                Icons.Outlined.Reorder,
                                contentDescription = stringResource(R.string.reorder_playlist),
                            )
                            Text(
                                stringResource(R.string.reorder),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                playlistVideos,
                key = { _, video -> video.id },
                contentType = { _, _ -> "video" },
            ) { index, video ->
                VideoCard(
                    video = video,
                    isCurrent = video.id == currentVideoId,
                    isPlaying = isPlaying,
                    index = index,
                    download = downloads[video.id],
                    selected = video.id in selectedVideoIds,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            if (video.id in selectedVideoIds) selectedVideoIds.remove(video.id)
                            else selectedVideoIds.add(video.id)
                            if (selectedVideoIds.isEmpty()) leaveSelectionMode()
                        } else {
                            onPlayFromHere(video.id)
                        }
                    },
                    onLongClick = {
                        selectionMode = true
                        if (video.id !in selectedVideoIds) selectedVideoIds.add(video.id)
                    },
                )
            }
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .testTag("playlist-selection-bar"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (compactLayout) selectedVideoIds.size.toString() else pluralStringResource(
                            R.plurals.selected_count,
                            selectedVideoIds.size,
                            selectedVideoIds.size,
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    selectedVideoIds.singleOrNull()?.let { selectedVideoId ->
                        if (compactLayout) IconButton(
                            onClick = { leaveSelectionMode(); onPlayFromHere(selectedVideoId) },
                            modifier = Modifier.testTag("playlist-selection-play-from-here"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play_from_here))
                        } else
                        Button(
                            onClick = {
                                leaveSelectionMode()
                                onPlayFromHere(selectedVideoId)
                            },
                            modifier = Modifier.testTag("playlist-selection-play-from-here"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(stringResource(R.string.play_from_here))
                        }
                    }
                    IconButton(
                        onClick = { onAddSelectionToPlaylist(selectedVideoIds.toList()) },
                        modifier = Modifier.testTag("playlist-selection-add"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_to_playlist),
                        )
                    }
                    IconButton(
                        onClick = { confirmRemoval = true },
                        modifier = Modifier.testTag("playlist-selection-remove"),
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.remove_from_playlist),
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            playlist = playlist,
            onDismiss = { showRenameDialog = false },
            onRename = onRename,
        )
    }
    if (showReorderDialog) {
        ReorderPlaylistDialog(
            videos = playlistVideos,
            onDismiss = { showReorderDialog = false },
            onConfirm = { orderedIds ->
                onReorder(orderedIds)
                showReorderDialog = false
            },
        )
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text(stringResource(R.string.remove_from_playlist_title)) },
            text = { Text(stringResource(R.string.remove_from_playlist_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveVideos(selectedVideoIds.toList())
                        confirmRemoval = false
                        leaveSelectionMode()
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showAutomaticDownloads) {
        ModalBottomSheet(
            onDismissRequest = { showAutomaticDownloads = false },
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.automatic_downloads),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.playlist_automatic_downloads_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!automaticDownloadsEnabled) {
                    Text(
                        stringResource(R.string.automatic_downloads_globally_disabled),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PlaylistAutomaticDownloadRow(
                    title = stringResource(R.string.audio_only),
                    icon = Icons.Outlined.MusicNote,
                    checked = DownloadMediaType.Audio in automaticDownloadMediaTypes,
                    enabled = automaticDownloadsEnabled ||
                        DownloadMediaType.Audio in automaticDownloadMediaTypes,
                    onCheckedChange = {
                        onAutomaticDownloadChange(DownloadMediaType.Audio, it)
                    },
                )
                PlaylistAutomaticDownloadRow(
                    title = stringResource(R.string.video),
                    icon = Icons.Outlined.VideoLibrary,
                    checked = DownloadMediaType.Video in automaticDownloadMediaTypes,
                    enabled = automaticDownloadsEnabled ||
                        DownloadMediaType.Video in automaticDownloadMediaTypes,
                    onCheckedChange = {
                        onAutomaticDownloadChange(DownloadMediaType.Video, it)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistAutomaticDownloadRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
