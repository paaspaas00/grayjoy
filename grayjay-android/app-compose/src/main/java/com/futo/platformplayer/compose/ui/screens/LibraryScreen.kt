package com.futo.platformplayer.compose.ui.screens

import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal enum class LibraryFilter(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    History(R.string.history, Icons.Outlined.History),
    WatchLater(R.string.watch_later, Icons.Outlined.Schedule),
    Playlists(R.string.playlists, Icons.AutoMirrored.Outlined.PlaylistPlay),
    Downloads(R.string.downloads, Icons.Outlined.Download),
}

internal fun videosForLibraryFilter(
    videos: List<VideoUiModel>,
    filter: LibraryFilter,
    downloads: Map<String, DownloadUiModel> = emptyMap(),
): List<VideoUiModel> = when (filter) {
    LibraryFilter.WatchLater -> videos.filter(VideoUiModel::isWatchLater)
    LibraryFilter.Playlists -> videos.filter { it.playlistNames.isNotEmpty() }
    LibraryFilter.Downloads -> videos.filter { it.isDownloaded || it.id in downloads }
    LibraryFilter.History -> videos
        .filter { it.lastWatchedAt > 0L || it.watchProgress > 0f }
        .sortedByDescending(VideoUiModel::lastWatchedAt)
}

internal data class DownloadExportAvailability(
    val canExportVideo: Boolean,
    val canExportAudio: Boolean,
)

internal fun downloadExportAvailability(
    selectedVideoIds: Collection<String>,
    downloads: Map<String, DownloadUiModel>,
): DownloadExportAvailability {
    fun allComplete(mediaType: DownloadMediaType): Boolean =
        selectedVideoIds.isNotEmpty() && selectedVideoIds.all { videoId ->
            downloads[videoId]?.isComplete(mediaType) == true
        }
    return DownloadExportAvailability(
        canExportVideo = allComplete(DownloadMediaType.Video),
        canExportAudio = allComplete(DownloadMediaType.Audio),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    videos: List<VideoUiModel>,
    playlists: List<PlaylistUiModel>,
    downloads: Map<String, DownloadUiModel> = emptyMap(),
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onAddSelectionToPlaylist: (List<String>) -> Unit,
    onRemoveSelectionFromHistory: (List<String>) -> Unit,
    onRemoveDownloads: (List<String>) -> Unit = {},
    onExportDownloads: (List<String>, DownloadMediaType, Uri) -> Unit = { _, _, _ -> },
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
) {
    var selectedFilterName by rememberSaveable { mutableStateOf(LibraryFilter.History.name) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var pendingExportMediaType by rememberSaveable { mutableStateOf<DownloadMediaType?>(null) }
    var renamingPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedVideoIds = remember { mutableStateListOf<String>() }
    val filters = LibraryFilter.entries
    val selectedFilter = LibraryFilter.valueOf(selectedFilterName)
    val pagerState = rememberPagerState(
        initialPage = filters.indexOf(selectedFilter).coerceAtLeast(0),
        pageCount = { filters.size },
    )
    val filterListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val activeFilterName by rememberUpdatedState(selectedFilterName)

    fun leaveSelectionMode() {
        selectionMode = false
        selectedVideoIds.clear()
    }

    val exportDirectoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { directoryUri ->
        val mediaType = pendingExportMediaType
        pendingExportMediaType = null
        if (directoryUri != null && mediaType != null && selectedVideoIds.isNotEmpty()) {
            onExportDownloads(selectedVideoIds.toList(), mediaType, directoryUri)
            leaveSelectionMode()
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                filters.getOrNull(page)?.let { filter ->
                    if (filter.name != activeFilterName) {
                        selectedFilterName = filter.name
                        leaveSelectionMode()
                    }
                }
            }
    }
    LaunchedEffect(selectedFilterName) {
        val page = filters.indexOfFirst { it.name == selectedFilterName }
        if (page >= 0) filterListState.animateScrollToItem(page)
        if (page >= 0 && page != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(page)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.library_tagline), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.library_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyRow(
            state = filterListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 10.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = filters,
                key = { _, filter -> filter.name },
            ) { page, filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        selectedFilterName = filter.name
                        leaveSelectionMode()
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    },
                    modifier = Modifier.testTag("library-filter-${filter.name.lowercase()}"),
                    leadingIcon = { Icon(filter.icon, contentDescription = null) },
                    label = { Text(stringResource(filter.labelRes)) },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("library-filter-pager"),
                beyondViewportPageCount = 1,
                key = { filters[it].name },
            ) { page ->
                val pageFilter = filters[page]
                val pageVideos = videosForLibraryFilter(videos, pageFilter, downloads)
                val isSelectedPage = pageFilter == selectedFilter
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(
                            if (isSelectedPage) "library-list"
                            else "library-list-${pageFilter.name.lowercase()}",
                        ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        top = 6.dp,
                        end = 16.dp,
                        bottom = if (
                            selectionMode && pageFilter in setOf(
                                LibraryFilter.History,
                                LibraryFilter.Downloads,
                            )
                        ) {
                            88.dp
                        } else {
                            16.dp
                        },
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(pageFilter.labelRes), style = MaterialTheme.typography.titleLarge)
                if (
                    pageFilter in setOf(LibraryFilter.History, LibraryFilter.Downloads) &&
                    selectionMode
                ) {
                    TextButton(
                        onClick = ::leaveSelectionMode,
                        modifier = Modifier.testTag(
                            if (pageFilter == LibraryFilter.History) {
                                "history-select-inline"
                            } else {
                                "downloads-select-inline"
                            },
                        ),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
        item {
            LibrarySummary(
                filter = pageFilter,
                videos = pageVideos,
                playlistCount = playlists.size,
            )
        }
        if (pageFilter == LibraryFilter.Playlists) {
            items(playlists, key = PlaylistUiModel::id) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onRename = { renamingPlaylistId = playlist.id },
                )
            }
        } else {
            itemsIndexed(
                items = pageVideos,
                key = { _, video -> video.id },
            ) { index, video ->
                VideoCard(
                    video = video,
                    index = index,
                    download = downloads[video.id],
                    selected = pageFilter in setOf(
                        LibraryFilter.History,
                        LibraryFilter.Downloads,
                    ) && video.id in selectedVideoIds,
                    showProgress = pageFilter == LibraryFilter.History,
                    onClick = {
                        if (
                            pageFilter in setOf(LibraryFilter.History, LibraryFilter.Downloads) &&
                            selectionMode
                        ) {
                            if (video.id in selectedVideoIds) selectedVideoIds.remove(video.id)
                            else selectedVideoIds.add(video.id)
                            if (selectedVideoIds.isEmpty()) leaveSelectionMode()
                        } else {
                            onVideoClick(video)
                        }
                    },
                    onLongClick = {
                        if (pageFilter in setOf(LibraryFilter.History, LibraryFilter.Downloads)) {
                            selectionMode = true
                            if (video.id !in selectedVideoIds) selectedVideoIds.add(video.id)
                        } else {
                            onVideoLongClick(video)
                        }
                    },
                )
            }
        }
        if (
            (pageFilter == LibraryFilter.Playlists && playlists.isEmpty()) ||
            (pageFilter != LibraryFilter.Playlists && pageVideos.isEmpty())
        ) {
            item {
                Text(
                    stringResource(R.string.nothing_here_yet),
                    modifier = Modifier.padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
                }
            }

            if (
                selectedFilter in setOf(LibraryFilter.History, LibraryFilter.Downloads) &&
                selectionMode
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .testTag(
                            if (selectedFilter == LibraryFilter.History) {
                                "history-selection-bar"
                            } else {
                                "downloads-selection-bar"
                            },
                        ),
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
                        if (selectedFilter == LibraryFilter.History) {
                            IconButton(
                                onClick = { onAddSelectionToPlaylist(selectedVideoIds.toList()) },
                                enabled = selectedVideoIds.isNotEmpty(),
                                modifier = Modifier.testTag("history-add-to-playlist"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.PlaylistAdd,
                                    contentDescription = stringResource(R.string.add_to_playlist),
                                )
                            }
                        } else {
                            TextButton(
                                onClick = { showExportSheet = true },
                                enabled = selectedVideoIds.isNotEmpty(),
                                modifier = Modifier.testTag("downloads-export"),
                            ) {
                                Icon(
                                    Icons.Outlined.FileUpload,
                                    contentDescription = null,
                                )
                                Text(stringResource(R.string.export))
                            }
                        }
                        IconButton(
                            onClick = { confirmRemoval = true },
                            enabled = selectedVideoIds.isNotEmpty(),
                            modifier = Modifier.testTag(
                                if (selectedFilter == LibraryFilter.History) {
                                    "history-remove"
                                } else {
                                    "downloads-remove"
                                },
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.remove),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = {
                Text(
                    stringResource(
                        if (selectedFilter == LibraryFilter.Downloads) {
                            R.string.remove_downloads_title
                        } else {
                            R.string.remove_from_history_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (selectedFilter == LibraryFilter.Downloads) {
                            R.string.remove_downloads_body
                        } else {
                            R.string.remove_from_history_body
                        },
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedFilter == LibraryFilter.Downloads) {
                            onRemoveDownloads(selectedVideoIds.toList())
                        } else {
                            onRemoveSelectionFromHistory(selectedVideoIds.toList())
                        }
                        confirmRemoval = false
                        leaveSelectionMode()
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showExportSheet) {
        val availability = downloadExportAvailability(selectedVideoIds, downloads)
        DownloadExportSheet(
            availability = availability,
            onDismiss = { showExportSheet = false },
            onChoose = { mediaType ->
                showExportSheet = false
                pendingExportMediaType = mediaType
                exportDirectoryLauncher.launch(null)
            },
        )
    }
    playlists.firstOrNull { it.id == renamingPlaylistId }?.let { playlist ->
        RenamePlaylistDialog(
            playlist = playlist,
            onDismiss = { renamingPlaylistId = null },
            onRename = { title -> onRenamePlaylist(playlist.id, title) },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DownloadExportSheet(
    availability: DownloadExportAvailability,
    onDismiss: () -> Unit,
    onChoose: (DownloadMediaType) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.export_downloads),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            ExportMediaTypeRow(
                title = stringResource(R.string.export_video),
                unavailableMessage = stringResource(R.string.export_video_unavailable),
                enabled = availability.canExportVideo,
                tag = "export-downloads-video",
                onClick = { onChoose(DownloadMediaType.Video) },
            )
            ExportMediaTypeRow(
                title = stringResource(R.string.export_audio),
                unavailableMessage = stringResource(R.string.export_audio_unavailable),
                enabled = availability.canExportAudio,
                tag = "export-downloads-audio",
                onClick = { onChoose(DownloadMediaType.Audio) },
            )
        }
    }
}

@Composable
private fun ExportMediaTypeRow(
    title: String,
    unavailableMessage: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (enabled) stringResource(R.string.choose_export_folder) else unavailableMessage,
            )
        },
        leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            headlineColor = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            supportingColor = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            leadingIconColor = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        ),
    )
}


@Composable
private fun LibrarySummary(
    filter: LibraryFilter,
    videos: List<VideoUiModel>,
    playlistCount: Int,
) {
    val supportingText = when (filter) {
        LibraryFilter.WatchLater -> pluralStringResource(
            R.plurals.videos_queued, videos.size, videos.size,
        )
        LibraryFilter.Playlists -> pluralStringResource(
            R.plurals.local_collections, playlistCount, playlistCount,
        )
        LibraryFilter.Downloads -> pluralStringResource(
            R.plurals.videos_offline, videos.size, videos.size,
        )
        LibraryFilter.History -> pluralStringResource(
            R.plurals.watched_videos_on_device, videos.size, videos.size,
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-summary-${filter.name.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                filter.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(filter.labelRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    supportingText,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
