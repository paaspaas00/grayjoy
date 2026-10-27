package com.futo.platformplayer.compose.jobs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.GrayjayUiState
import com.futo.platformplayer.compose.ui.DownloadStatus

internal fun notifyJobFinished(context: Context, label: Int) {
    com.futo.platformplayer.compose.diagnostics.CrashLogStore.recordEvent("Jobs", "finished")
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context.applicationContext, context.getString(R.string.job_finished_notice, context.getString(label)), Toast.LENGTH_SHORT).show()
    }
}

/** A download batch is one job; paused work and profile changes are not completion events. */
internal class JobCompletionTracker {
    private var previous: GrayjayUiState? = null
    private var completedTransferInBatch = false
    fun observe(state: GrayjayUiState): List<Int> {
        val old = previous
        previous = state
        if (old == null || old.activeProfileId != state.activeProfileId || state.backgroundJobsSuspended) {
            completedTransferInBatch = false
            return emptyList()
        }
        return buildList {
            if (state.downloadCompletionGeneration > old.downloadCompletionGeneration) completedTransferInBatch = true
            if (state.downloadBatchProgress.total < old.downloadBatchProgress.total &&
                old.downloadBatchProgress.completed < old.downloadBatchProgress.total) completedTransferInBatch = false
            if (old.downloads !== state.downloads || old.activePlaylistDownloads != state.activePlaylistDownloads ||
                old.downloadCompletionGeneration != state.downloadCompletionGeneration) {
                val runnable = state.activePlaylistDownloads.isNotEmpty() || state.downloads.values.any {
                    it.status in setOf(DownloadStatus.Preparing, DownloadStatus.Queued, DownloadStatus.Downloading)
                }
                if (!runnable) {
                    if (completedTransferInBatch && state.downloadBatchProgress.total > 0 &&
                        state.downloadBatchProgress.completed == state.downloadBatchProgress.total &&
                        state.downloads.values.none { it.status == DownloadStatus.Paused }) add(R.string.downloads)
                    completedTransferInBatch = false
                }
            }
            fun finished(before: Boolean, after: Boolean, label: Int) { if (before && !after) add(label) }
            finished(old.youtubeImport.isRunning, state.youtubeImport.isRunning, R.string.import_from_youtube)
            if (state.databaseImport.preview == null && !state.databaseImport.passwordRequired)
                finished(old.databaseImport.isBusy, state.databaseImport.isBusy, R.string.importing_data)
            finished(old.sourceOperationInProgress, state.sourceOperationInProgress, R.string.source_operation)
            finished(old.sourceProfileSetup.isRunning, state.sourceProfileSetup.isRunning, R.string.profile_split_title)
        }
    }
}
