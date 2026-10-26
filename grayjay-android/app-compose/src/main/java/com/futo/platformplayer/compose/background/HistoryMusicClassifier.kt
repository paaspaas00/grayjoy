package com.futo.platformplayer.compose.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.futo.platformplayer.backend.YouTubeMusicMetadataResolver
import com.futo.platformplayer.compose.data.SharedPreferencesLibraryRepository
import com.futo.platformplayer.compose.data.wasProfileDeleted
import com.futo.platformplayer.compose.ui.VideoUiModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal fun needsMusicClassification(video: VideoUiModel): Boolean =
    video.sourceId.equals("youtube", true) && video.isMusic == null &&
        (video.lastWatchedAt > 0L || video.watchProgress > 0f || video.isDownloaded)

/** One persisted queue per profile, independent of the library screen and its filters. */
internal object HistoryMusicClassifier {
    @Volatile var deferForPlaybackBuffering = false
    const val PROFILE = "profile"
    const val COMPLETED = "completed"
    const val TOTAL = "total"
    fun tag(profile: String) = "history-music-$profile"
    fun schedule(context: Context, profile: String) {
        enqueue(context, profile, ExistingWorkPolicy.KEEP)
    }
    fun continueQueue(context: Context, profile: String) {
        enqueue(context, profile, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }
    private fun enqueue(context: Context, profile: String, policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniqueWork(tag(profile), policy,
            OneTimeWorkRequestBuilder<HistoryMusicWorker>()
                .setInputData(workDataOf(PROFILE to profile)).addTag(tag(profile))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build())
    }
}

class HistoryMusicWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profile = inputData.getString(HistoryMusicClassifier.PROFILE) ?: return@withContext Result.failure()
        if (wasProfileDeleted(applicationContext, profile)) return@withContext Result.success()
        val repository = SharedPreferencesLibraryRepository(applicationContext, profile)
        val ledger = applicationContext.getSharedPreferences("music_classification_$profile", Context.MODE_PRIVATE)
        val resolver = YouTubeMusicMetadataResolver()
        synchronized(repository.transactionLock) {
            val recovered = repository.loadSavedVideos().filter(::needsMusicClassification).mapNotNull { video ->
                val classification = when (ledger.getLong(video.id, 0L)) { 1L -> true; 2L -> false; else -> null }
                classification?.let { video.copy(isMusic = it) }
            }
            if (recovered.isNotEmpty()) repository.saveVideos(recovered)
        }
        val history = repository.loadSavedVideos().filter { it.sourceId.equals("youtube", true) &&
            (it.lastWatchedAt > 0L || it.watchProgress > 0f || it.isDownloaded) }
        val initial = history.filter(::needsMusicClassification)
        // A successful page without a category is still processed: it stays in All, not silently
        // labelled non-music. Only transport/server failures receive a delayed retry.
        val candidates = initial.filter { ledger.getLong(it.id, 0L) != Long.MAX_VALUE }
        var retryNeeded = false
        var continueImmediately = false
        var completed = history.count { it.isMusic != null || ledger.getLong(it.id, 0L) == Long.MAX_VALUE }
        setProgress(workDataOf(HistoryMusicClassifier.COMPLETED to completed, HistoryMusicClassifier.TOTAL to history.size))
        val started = android.os.SystemClock.elapsedRealtime()
        val pending = linkedMapOf<String, Boolean>()
        fun flush() {
            if (pending.isEmpty()) return
            synchronized(repository.transactionLock) {
                if (wasProfileDeleted(applicationContext, profile)) { pending.clear(); return }
                val patches = repository.loadSavedVideos().mapNotNull { video ->
                    pending[video.id]?.takeIf { video.isMusic == null }?.let { video.copy(isMusic = it) }
                }
                repository.saveVideos(patches)
            }
            pending.clear()
        }
        try {
            for ((index, video) in candidates.withIndex()) {
                coroutineContext.ensureActive()
                if (wasProfileDeleted(applicationContext, profile)) return@withContext Result.success()
                while (HistoryMusicClassifier.deferForPlaybackBuffering) delay(1000L)
                if (android.os.SystemClock.elapsedRealtime() - started > TimeUnit.MINUTES.toMillis(8)) {
                    continueImmediately = true
                    break
                }
                val now = System.currentTimeMillis()
                if (ledger.getLong(video.id, 0L) > now) { retryNeeded = true; continue }
                val result = resolver.classifyWithStatus(video.contentUrl.ifBlank { video.id })
                coroutineContext.ensureActive()
                if (wasProfileDeleted(applicationContext, profile)) return@withContext Result.success()
                val classification = result.isMusic
                if (classification != null) {
                    // A tiny durable checkpoint prevents repeat requests after an abrupt process
                    // kill; the larger library JSON can still be committed in efficient batches.
                    check(ledger.edit().putLong(video.id, if (classification) 1L else 2L).commit())
                    pending[video.id] = classification
                    completed++
                } else {
                    check(ledger.edit().putLong(video.id, if (result.retryable)
                        now + TimeUnit.HOURS.toMillis(6) else Long.MAX_VALUE).commit())
                    retryNeeded = retryNeeded || result.retryable
                    if (!result.retryable) completed++
                }
                if (pending.size >= 16 || index == candidates.lastIndex) flush()
                setProgress(workDataOf(HistoryMusicClassifier.COMPLETED to completed,
                    HistoryMusicClassifier.TOTAL to history.size))
                delay(150L)
            }
            flush()
            val processedIds = candidates.mapTo(hashSetOf(), VideoUiModel::id)
            if (repository.loadSavedVideos().any { needsMusicClassification(it) && it.id !in processedIds &&
                ledger.getLong(it.id, 0L) != Long.MAX_VALUE }) continueImmediately = true
            // Prune only abandoned ledger entries, never reclassify completed unknown records.
            val ids = repository.loadSavedVideos().mapTo(hashSetOf(), VideoUiModel::id)
            val editor = ledger.edit()
            ledger.all.keys.filterNot(ids::contains).forEach(editor::remove)
            editor.commit()
            if (continueImmediately) {
                HistoryMusicClassifier.continueQueue(applicationContext, profile)
                Result.success()
            } else if (retryNeeded) Result.retry() else {
                if (candidates.isNotEmpty()) com.futo.platformplayer.compose.jobs.notifyJobFinished(applicationContext,
                    com.futo.platformplayer.compose.R.string.history_music_background_job)
                Result.success()
            }
        } finally {
            // Persist completed metadata even if Android stops this worker between chunks.
            withContext(kotlinx.coroutines.NonCancellable) { flush() }
            resolver.close()
        }
    }
}
