package com.futo.platformplayer.compose.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import java.text.DecimalFormat

private data class StorageVideoItem(
    val video: VideoUiModel,
    val download: DownloadUiModel,
    val bytes: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageManagementScreen(
    videos: List<VideoUiModel>,
    downloads: Map<String, DownloadUiModel>,
    onDismiss: () -> Unit,
    onRemove: (List<String>) -> Unit,
    onExport: (List<String>, DownloadMediaType, Uri) -> Unit,
) {
    var descending by rememberSaveable { mutableStateOf(true) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var exportMediaTypeName by rememberSaveable {
        mutableStateOf(DownloadMediaType.Video.name)
    }
    val selectedIds = remember { mutableStateListOf<String>() }
    val videoIndex = remember(videos) { videos.associateBy(VideoUiModel::id) }
    val items = remember(videos, downloads, descending) {
        downloads.values.mapNotNull { download ->
            if (!download.isComplete) return@mapNotNull null
            val video = videoIndex[download.videoId] ?: return@mapNotNull null
            StorageVideoItem(
                video = video,
                download = download,
                bytes = (download.contentLength ?: download.bytesDownloaded).coerceAtLeast(0L),
            )
        }.sortedWith(
            if (descending) compareByDescending(StorageVideoItem::bytes)
            else compareBy(StorageVideoItem::bytes),
        )
    }
    val totalBytes = remember(items) { items.sumOf(StorageVideoItem::bytes) }
    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null && selectedIds.isNotEmpty()) {
            onExport(
                selectedIds.toList(),
                DownloadMediaType.valueOf(exportMediaTypeName),
                uri,
            )
            selectedIds.clear()
        }
        showExport = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.storage_management)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.close),
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { descending = !descending },
                                modifier = Modifier.testTag("storage-sort"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = stringResource(
                                        if (descending) R.string.smallest_first
                                        else R.string.largest_first,
                                    ),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    if (selectedIds.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    selectedIds.size.toString(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                IconButton(onClick = { showExport = true }) {
                                    Icon(
                                        Icons.Outlined.FileUpload,
                                        contentDescription = stringResource(R.string.export),
                                    )
                                }
                                IconButton(onClick = { confirmDelete = true }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.remove),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .testTag("storage-downloads-list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.downloaded_media),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(
                                    R.string.storage_total,
                                    items.size,
                                    formatFileSize(totalBytes),
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(items, key = { it.video.id }) { item ->
                        val selected = item.video.id in selectedIds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            if (selected) selectedIds.remove(item.video.id)
                                            else selectedIds.add(item.video.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selected) selectedIds.add(item.video.id)
                                    },
                                )
                                .testTag("storage-item-${item.video.id}"),
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (selectedIds.isNotEmpty()) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            if (it) selectedIds.add(item.video.id)
                                            else selectedIds.remove(item.video.id)
                                        },
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.video.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        item.video.creator,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    formatFileSize(item.bytes),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    if (items.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_downloaded_media),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExport) {
        val selectedDownloads = selectedIds.mapNotNull(downloads::get)
        val videoAvailable = selectedDownloads.all {
            it.isComplete(DownloadMediaType.Video)
        }
        val audioAvailable = selectedDownloads.all {
            it.isComplete(DownloadMediaType.Audio)
        }
        ModalBottomSheet(
            onDismissRequest = { showExport = false },
            contentWindowInsets = { grayjoySheetInsets() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.export_downloads), style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = exportMediaTypeName == DownloadMediaType.Video.name,
                        onClick = { exportMediaTypeName = DownloadMediaType.Video.name },
                        enabled = videoAvailable,
                        label = { Text(stringResource(R.string.video)) },
                    )
                    FilterChip(
                        selected = exportMediaTypeName == DownloadMediaType.Audio.name,
                        onClick = { exportMediaTypeName = DownloadMediaType.Audio.name },
                        enabled = audioAvailable,
                        label = { Text(stringResource(R.string.audio_only)) },
                    )
                }
                if (!videoAvailable || !audioAvailable) {
                    Text(
                        stringResource(R.string.storage_export_missing_format),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { directoryPicker.launch(null) },
                    enabled = when (DownloadMediaType.valueOf(exportMediaTypeName)) {
                        DownloadMediaType.Video -> videoAvailable
                        DownloadMediaType.Audio -> audioAvailable
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.choose_export_folder))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove_downloads_title)) },
            text = { Text(stringResource(R.string.remove_downloads_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(selectedIds.toList())
                        selectedIds.clear()
                        confirmDelete = false
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

internal fun formatFileSize(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    val formatter = DecimalFormat("0.#")
    return when {
        value >= 1_073_741_824.0 -> "${formatter.format(value / 1_073_741_824.0)} GB"
        value >= 1_048_576.0 -> "${formatter.format(value / 1_048_576.0)} MB"
        value >= 1_024.0 -> "${formatter.format(value / 1_024.0)} KB"
        else -> "${bytes.coerceAtLeast(0L)} B"
    }
}
