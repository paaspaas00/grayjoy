package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.BackgroundYoutubeImportUiState
import com.futo.platformplayer.compose.ui.DatabaseImportUiState
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.UpdateDownloadUiModel
import com.futo.platformplayer.compose.ui.LibraryTransferUiState
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.YoutubeImportStageUi
import com.futo.platformplayer.compose.ui.YoutubeImportUiState

internal data class ActiveJobItem(
    val id: String,
    val title: String,
    val detail: String,
    val progress: Float?,
    val icon: ImageVector,
    val cancellable: Boolean = false,
)

@Composable
internal fun rememberActiveJobItems(
    youtubeImport: YoutubeImportUiState,
    backgroundYoutubeImport: BackgroundYoutubeImportUiState,
    databaseImport: DatabaseImportUiState,
    downloads: Map<String, DownloadUiModel>,
    videos: List<VideoUiModel>,
    sourceOperationInProgress: Boolean,
    sourceOperationMessage: String?,
    updateDownload: UpdateDownloadUiModel?,
    libraryTransfer: LibraryTransferUiState,
): List<ActiveJobItem> {
    val activeDownloads = remember(downloads) { downloads.values.filter(DownloadUiModel::isActive) }
    val activeDownloadProgress = activeDownloads.mapNotNull(DownloadUiModel::progress)
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
    val downloadDetail = if (activeDownloads.size == 1) {
        val id = activeDownloads.single().videoId
        videos.firstOrNull { it.id == id }?.title
            ?: stringResource(R.string.download_in_progress)
    } else {
        pluralStringResource(
            R.plurals.active_downloads,
            activeDownloads.size,
            activeDownloads.size,
        )
    }
    return buildList {
        if (youtubeImport.isRunning) add(
            ActiveJobItem(
                id = "youtube-import",
                title = stringResource(R.string.import_from_youtube),
                detail = youtubeImport.progressLabel(),
                progress = youtubeImport.progressFraction(),
                icon = Icons.Outlined.Sync,
                cancellable = true,
            ),
        )
        if (backgroundYoutubeImport.isRunning) add(
            ActiveJobItem(
                id = "youtube-import-background",
                title = stringResource(R.string.automatic_youtube_import),
                detail = backgroundYoutubeImport.progressLabel(),
                progress = backgroundYoutubeImport.progressFraction(),
                icon = Icons.Outlined.Sync,
                cancellable = true,
            ),
        )
        if (activeDownloads.isNotEmpty()) add(
            ActiveJobItem(
                id = "downloads",
                title = stringResource(R.string.downloads),
                detail = downloadDetail,
                progress = activeDownloadProgress,
                icon = Icons.Outlined.Download,
            ),
        )
        if (databaseImport.isBusy) add(
            ActiveJobItem(
                id = "database-import",
                title = stringResource(R.string.importing_data),
                detail = databaseImport.fileName.ifBlank {
                    stringResource(R.string.please_wait)
                },
                progress = null,
                icon = Icons.Outlined.Sync,
            ),
        )
        if (sourceOperationInProgress) add(
            ActiveJobItem(
                id = "source-operation",
                title = stringResource(R.string.source_operation),
                detail = sourceOperationMessage ?: stringResource(R.string.please_wait),
                progress = null,
                icon = Icons.Outlined.Sync,
            ),
        )
        updateDownload?.let { update ->
            add(
                ActiveJobItem(
                    id = "app-update",
                    title = stringResource(R.string.downloading_update_version, update.versionName),
                    detail = stringResource(R.string.download_in_progress),
                    progress = update.totalBytes?.takeIf { it > 0L }?.let { total ->
                        (update.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                    },
                    icon = Icons.Outlined.Download,
                ),
            )
        }
        if (libraryTransfer.isRunning) add(
            ActiveJobItem(
                id = "library-transfer",
                title = libraryTransfer.title,
                detail = stringResource(R.string.please_wait),
                progress = null,
                icon = Icons.Outlined.FileUpload,
            ),
        )
    }
}

@Composable
internal fun ActiveJobsButton(
    jobs: List<ActiveJobItem>,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val knownProgress = jobs.mapNotNull(ActiveJobItem::progress)
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.testTag("active-jobs-toggle"),
        ) {
            Icon(
                Icons.Outlined.WorkHistory,
                contentDescription = stringResource(R.string.background_jobs),
                tint = if (expanded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (jobs.isNotEmpty()) {
            if (knownProgress.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                CircularProgressIndicator(
                    progress = { knownProgress.average().toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
internal fun ActiveJobsPanel(
    visible: Boolean,
    jobs: List<ActiveJobItem>,
    onCancelYoutubeImports: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .testTag("active-jobs-panel"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.background_jobs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (jobs.isEmpty()) {
                    Text(
                        stringResource(R.string.no_active_jobs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                jobs.forEachIndexed { index, job ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(job.icon, contentDescription = null)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                job.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                job.detail,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            job.progress?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (job.cancellable) {
                            IconButton(onClick = onCancelYoutubeImports) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YoutubeImportUiState.progressLabel(): String = importProgressLabel(
    stage = stage,
    completed = completed,
    total = total,
    currentItemCompleted = currentItemCompleted,
)

@Composable
private fun BackgroundYoutubeImportUiState.progressLabel(): String = importProgressLabel(
    stage = stage,
    completed = completed,
    total = total,
    currentItemCompleted = currentItemCompleted,
)

@Composable
private fun importProgressLabel(
    stage: YoutubeImportStageUi?,
    completed: Int,
    total: Int?,
    currentItemCompleted: Int?,
): String {
    val stageLabel = stringResource(
        when (stage) {
            YoutubeImportStageUi.Subscriptions -> R.string.youtube_importing_subscriptions
            YoutubeImportStageUi.History -> R.string.youtube_importing_history
            YoutubeImportStageUi.Playlists -> R.string.youtube_importing_playlists
            YoutubeImportStageUi.Connecting, null -> R.string.youtube_import_connecting
        },
    )
    val progress = total?.takeIf { it > 0 }?.let {
        stringResource(R.string.youtube_import_progress_count, stageLabel, completed, it)
    } ?: if (completed > 0) {
        stringResource(R.string.youtube_import_progress_open, stageLabel, completed)
    } else {
        stageLabel
    }
    return currentItemCompleted?.let {
        "$progress · ${stringResource(R.string.youtube_import_playlist_video_count, it)}"
    } ?: progress
}

private fun YoutubeImportUiState.progressFraction(): Float? = total?.takeIf { it > 0 }
    ?.let { (completed.toFloat() / it).coerceIn(0f, 1f) }

private fun BackgroundYoutubeImportUiState.progressFraction(): Float? =
    total?.takeIf { it > 0 }?.let { (completed.toFloat() / it).coerceIn(0f, 1f) }
