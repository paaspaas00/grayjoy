package com.futo.platformplayer.compose.jobs

import android.content.Context
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.GrayjayUiState
import com.futo.platformplayer.compose.ui.UpdateDownloadUiModel

internal data class ActiveJobsSnapshot(val count: Int, val percent: Int, val description: String)

internal fun activeJobsSnapshot(
    context: Context,
    state: GrayjayUiState,
    update: UpdateDownloadUiModel?,
): ActiveJobsSnapshot {
    if (state.backgroundJobsSuspended) return ActiveJobsSnapshot(0, 0, "")
    val downloads = state.downloads.values.filter { it.isActive }
    val progress = downloads.mapTo(mutableListOf()) { it.progress ?: 0f }
    val descriptions = mutableListOf<String>()
    if (downloads.isNotEmpty()) {
        descriptions += if (state.downloadStorage.downloadsPaused) {
            context.getString(R.string.download_paused_low_storage)
        } else {
            context.resources.getQuantityString(R.plurals.active_downloads, downloads.size, downloads.size)
        }
    }
    fun addJob(running: Boolean, description: String, fraction: Float = 0f) {
        if (running) {
            progress += fraction
            descriptions += description
        }
    }
    with(state.youtubeImport) {
        addJob(isRunning, context.getString(R.string.import_from_youtube),
            total?.takeIf { it > 0 }?.let { completed.toFloat() / it } ?: 0f)
    }
    with(state.backgroundYoutubeImport) {
        addJob(isRunning, context.getString(R.string.automatic_youtube_import),
            total?.takeIf { it > 0 }?.let { completed.toFloat() / it } ?: 0f)
    }
    addJob(state.databaseImport.isBusy, context.getString(R.string.importing_data))
    addJob(state.sourceOperationInProgress, context.getString(R.string.source_operation))
    addJob(state.libraryTransfer.isRunning, state.libraryTransfer.title)
    state.mediaExports.forEach {
        addJob(true, context.getString(R.string.export_downloaded_media) + " · " + it.currentTitle, it.progress)
    }
    update?.let {
        addJob(true, context.getString(R.string.downloading_update_version, it.versionName),
            it.totalBytes?.takeIf { size -> size > 0 }?.let { size ->
                it.downloadedBytes.toFloat() / size
            } ?: 0f)
    }
    val percent = if (progress.isEmpty()) 0 else (
        progress.map { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }.average() * 100
    ).toInt()
    return ActiveJobsSnapshot(progress.size, percent, descriptions.joinToString(" · "))
}
