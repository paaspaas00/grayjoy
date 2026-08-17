package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.data.normalizedPlaylistTitle
import com.futo.platformplayer.compose.data.playlistTitleExists
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

internal fun playlistsMatchingQuery(
    playlists: List<PlaylistUiModel>,
    query: String,
): List<PlaylistUiModel> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return playlists
    return playlists.filter { playlist ->
        playlist.title.contains(normalizedQuery, ignoreCase = true)
    }
}

@Composable
internal fun PlaylistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it.take(80)) },
        modifier = modifier
            .fillMaxWidth()
            .testTag("playlist-search"),
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
        label = { Text(stringResource(R.string.search_playlists)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoActionsSheet(
    video: VideoUiModel,
    download: DownloadUiModel?,
    onDismiss: () -> Unit,
    onToggleDownload: () -> Unit,
    onDownloadAudio: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onPlayFromHere: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { grayjoySheetInsets() },
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    video.creator,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider()
            onPlayNext?.let { playNext ->
                VideoAction(
                    title = stringResource(R.string.play_next),
                    subtitle = stringResource(R.string.play_next_description),
                    icon = { Icon(Icons.Outlined.QueuePlayNext, contentDescription = null) },
                    tag = "video-action-play-next",
                    onClick = {
                        playNext()
                        onDismiss()
                    },
                )
            }
            onPlayFromHere?.let { playFromHere ->
                VideoAction(
                    title = stringResource(R.string.play_from_here),
                    subtitle = stringResource(R.string.play_from_here_description),
                    icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    tag = "video-action-play-from-here",
                    onClick = {
                        playFromHere()
                        onDismiss()
                    },
                )
            }
            if (!video.isLive) {
                val audioDownloadComplete = download?.isComplete(DownloadMediaType.Audio) == true
                val audioDownloadActive = download?.isActive(DownloadMediaType.Audio) == true
                val audioDownloadFailed = DownloadMediaType.Audio in download?.failedMediaTypes.orEmpty()
                VideoAction(
                    title = stringResource(
                        when {
                            audioDownloadComplete -> R.string.remove_download
                            audioDownloadFailed -> R.string.retry_download
                            audioDownloadActive -> R.string.cancel_download
                            else -> R.string.download_all_audio
                        },
                    ),
                    subtitle = download?.takeIf { it.hasAttempt(DownloadMediaType.Audio) }
                        ?.let { downloadStatusText(it, DownloadMediaType.Audio) }
                        ?: stringResource(R.string.download_description),
                    icon = {
                        Icon(
                            when {
                                audioDownloadComplete -> Icons.Outlined.DeleteOutline
                                audioDownloadActive -> Icons.Outlined.Close
                                else -> Icons.Outlined.Download
                            },
                            contentDescription = null,
                        )
                    },
                    tag = "video-action-download-audio",
                    onClick = {
                        onDownloadAudio()
                        onDismiss()
                    },
                )
            }
            if (!video.isLive) {
                val videoDownloadComplete = download?.isComplete(DownloadMediaType.Video) == true
                val videoDownloadActive = download?.isActive(DownloadMediaType.Video) == true
                val videoDownloadFailed = DownloadMediaType.Video in download?.failedMediaTypes.orEmpty()
                val downloadTitle = when {
                    videoDownloadComplete -> R.string.remove_download
                    videoDownloadFailed -> R.string.retry_download
                    videoDownloadActive -> R.string.cancel_download
                    else -> R.string.download
                }
                VideoAction(
                    title = stringResource(downloadTitle),
                    subtitle = download?.takeIf { it.hasAttempt(DownloadMediaType.Video) }
                        ?.let { downloadStatusText(it, DownloadMediaType.Video) }
                        ?: stringResource(R.string.download_description),
                    icon = {
                        Icon(
                            imageVector = when {
                                videoDownloadComplete -> Icons.Outlined.DeleteOutline
                                videoDownloadActive -> Icons.Outlined.Close
                                else -> Icons.Outlined.Download
                            },
                            contentDescription = null,
                        )
                    },
                    tag = "video-action-download",
                    onClick = {
                        onToggleDownload()
                        onDismiss()
                    },
                )
            }
            VideoAction(
                title = stringResource(R.string.share),
                subtitle = stringResource(R.string.send_original_video_link),
                icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                tag = "video-action-share",
                onClick = {
                    onShare()
                    onDismiss()
                },
            )
            VideoAction(
                title = stringResource(R.string.add_to_playlist),
                subtitle = stringResource(R.string.add_to_playlist_description),
                icon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null) },
                tag = "video-action-playlist",
                onClick = {
                    onAddToPlaylist()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
internal fun downloadStatusText(
    download: DownloadUiModel,
    mediaType: DownloadMediaType? = null,
): String {
    val effectiveStatus = when {
        mediaType == null -> download.status
        download.isComplete(mediaType) -> DownloadStatus.Completed
        mediaType in download.failedMediaTypes -> DownloadStatus.Failed
        download.isActive(mediaType) && download.status in setOf(
            DownloadStatus.Preparing,
            DownloadStatus.Queued,
            DownloadStatus.Downloading,
            DownloadStatus.Paused,
            DownloadStatus.Removing,
        ) -> download.status
        download.isActive(mediaType) -> DownloadStatus.Queued
        else -> download.status
    }
    val label = stringResource(
        when (effectiveStatus) {
            DownloadStatus.Preparing -> R.string.download_preparing
            DownloadStatus.Queued -> R.string.download_queued
            DownloadStatus.Downloading -> R.string.download_downloading
            DownloadStatus.Paused -> R.string.download_paused
            DownloadStatus.Completed -> R.string.download_complete
            DownloadStatus.Failed -> R.string.download_failed
            DownloadStatus.Removing -> R.string.download_removing
        },
    )
    val progress = download.progress?.let { " ${(it * 100f).toInt().coerceIn(0, 100)}%" }.orEmpty()
    val detail = download.errorMessage?.takeUnless { message ->
        message.contains("Exception", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("grayjay.internal", ignoreCase = true)
    }
    return if (effectiveStatus == DownloadStatus.Failed && !detail.isNullOrBlank()) {
        "$label: $detail"
    } else {
        "$label$progress"
    }
}

@Composable
private fun VideoAction(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    tag: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
    )
}

@Composable
internal fun PlaylistPickerDialog(
    playlists: List<PlaylistUiModel>,
    videoIds: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, List<String>) -> Unit,
    onCreate: (String, List<String>) -> Unit,
) {
    var creatingNew by rememberSaveable { mutableStateOf(playlists.isEmpty()) }
    var title by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val duplicateTitle = playlistTitleExists(title, playlists.map(PlaylistUiModel::title))
    val matchingPlaylists = playlistsMatchingQuery(playlists, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (creatingNew) R.string.new_playlist else R.string.add_to_playlist))
        },
        text = {
            if (creatingNew) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new-playlist-name"),
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) },
                    isError = duplicateTitle,
                    supportingText = if (duplicateTitle) {
                        { Text(stringResource(R.string.playlist_name_already_exists)) }
                    } else null,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaylistSearchField(
                        query = query,
                        onQueryChange = { query = it },
                    )
                    if (matchingPlaylists.isEmpty()) {
                        Text(
                            stringResource(R.string.no_playlists_found),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .testTag("playlist-choice-list"),
                        ) {
                            items(
                                items = matchingPlaylists,
                                key = PlaylistUiModel::id,
                            ) { playlist ->
                                ListItem(
                                    headlineContent = { Text(playlist.title) },
                                    supportingContent = {
                                        Text(
                                            pluralStringResource(
                                                R.plurals.video_count,
                                                playlist.videoIds.size,
                                                playlist.videoIds.size,
                                            ),
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAdd(playlist.id, videoIds)
                                            onDismiss()
                                        }
                                        .testTag("playlist-choice-${playlist.id}"),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                Button(
                    onClick = {
                        onCreate(title.trim(), videoIds)
                        onDismiss()
                    },
                    enabled = title.isNotBlank() && !duplicateTitle,
                    modifier = Modifier.testTag("create-playlist"),
                ) { Text(stringResource(R.string.create)) }
            } else {
                TextButton(onClick = { creatingNew = true }) { Text(stringResource(R.string.new_playlist)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun RenamePlaylistDialog(
    playlist: PlaylistUiModel,
    existingPlaylistTitles: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by rememberSaveable(playlist.id) { mutableStateOf(playlist.title) }
    val otherTitles = existingPlaylistTitles.filterNot {
        normalizedPlaylistTitle(it) == normalizedPlaylistTitle(playlist.title)
    }
    val duplicateTitle = playlistTitleExists(title, otherTitles)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_playlist)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename-playlist-name"),
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
                isError = duplicateTitle,
                supportingText = if (duplicateTitle) {
                    { Text(stringResource(R.string.playlist_name_already_exists)) }
                } else null,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onRename(title.trim())
                    onDismiss()
                },
                enabled = title.isNotBlank() && title.trim() != playlist.title && !duplicateTitle,
                modifier = Modifier.testTag("confirm-rename-playlist"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
