package com.futo.platformplayer.compose.background

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.data.LocalContentRepository
import com.futo.platformplayer.compose.data.SharedPreferencesLibraryRepository
import com.futo.platformplayer.compose.data.SharedPreferencesSourceRepository
import com.futo.platformplayer.compose.data.applyAccountImportTransaction
import com.futo.platformplayer.compose.engine.AndroidGrayjayEngine
import com.futo.platformplayer.compose.engine.EngineUserImportSelection
import com.futo.platformplayer.compose.ui.YoutubeImportInterval
import com.futo.platformplayer.compose.ui.YoutubeImportScheduleUiState
import com.futo.platformplayer.compose.ui.YoutubeImportSelection
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal object YoutubeImportScheduler {
    const val WORK_TAG = "grayjoy-youtube-account-import"
    const val KEY_STAGE = "stage"
    const val KEY_COMPLETED = "completed"
    const val KEY_TOTAL = "total"
    const val KEY_CURRENT_ITEM = "current_item"

    private const val PREFERENCES = "grayjoy_youtube_import_schedule_v1"

    fun scheduleFor(
        context: Context,
        profileId: String,
        sourceId: String = "youtube",
    ): YoutubeImportScheduleUiState {
        val json = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val prefix = key(profileId, sourceId)
        val interval = runCatching {
            YoutubeImportInterval.valueOf(json.getString("$prefix.interval", null).orEmpty())
        }.getOrDefault(YoutubeImportInterval.Off)
        return YoutubeImportScheduleUiState(
            sourceId = sourceId,
            interval = interval,
            selection = YoutubeImportSelection(
                subscriptions = json.getBoolean("$prefix.subscriptions", true),
                history = json.getBoolean("$prefix.history", true),
                playlists = json.getBoolean("$prefix.playlists", true),
                likedVideos = json.getBoolean("$prefix.liked", true),
            ),
        )
    }

    fun update(
        context: Context,
        profileId: String,
        state: YoutubeImportScheduleUiState,
    ) {
        val appContext = context.applicationContext
        val prefix = key(profileId, state.sourceId)
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("$prefix.interval", state.interval.name)
            .putBoolean("$prefix.subscriptions", state.selection.subscriptions)
            .putBoolean("$prefix.history", state.selection.history)
            .putBoolean("$prefix.playlists", state.selection.playlists)
            .putBoolean("$prefix.liked", state.selection.likedVideos)
            .apply()
        val manager = WorkManager.getInstance(appContext)
        val workName = uniqueWorkName(profileId, state.sourceId)
        if (state.interval == YoutubeImportInterval.Off) {
            manager.cancelUniqueWork(workName)
            return
        }
        val input = Data.Builder()
            .putString(YoutubeAccountImportWorker.KEY_PROFILE_ID, profileId)
            .putString(YoutubeAccountImportWorker.KEY_SOURCE_ID, state.sourceId)
            .putBoolean(YoutubeAccountImportWorker.KEY_SUBSCRIPTIONS, state.selection.subscriptions)
            .putBoolean(YoutubeAccountImportWorker.KEY_HISTORY, state.selection.history)
            .putBoolean(YoutubeAccountImportWorker.KEY_PLAYLISTS, state.selection.playlists)
            .putBoolean(YoutubeAccountImportWorker.KEY_LIKED, state.selection.likedVideos)
            .build()
        val request = PeriodicWorkRequestBuilder<YoutubeAccountImportWorker>(
            state.interval.hours,
            TimeUnit.HOURS,
        )
            .setInitialDelay(state.interval.hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setInputData(input)
            .addTag(WORK_TAG)
            .addTag(profileTag(profileId))
            .build()
        manager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelRunningAndReschedule(
        context: Context,
        profileId: String,
        workId: String,
        sourceId: String = "youtube",
    ) {
        runCatching { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(workId)) }
        update(context, profileId, scheduleFor(context, profileId, sourceId))
    }

    fun clearProfile(context: Context, profileId: String) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelAllWorkByTag(profileTag(profileId))
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().apply {
            preferences.all.keys
                .filter { it.startsWith("$profileId.") }
                .forEach(::remove)
        }.apply()
    }

    fun profileTag(profileId: String): String = "$WORK_TAG:profile:$profileId"
    fun uniqueWorkName(profileId: String, sourceId: String): String =
        "$WORK_TAG:$profileId:$sourceId"

    private fun key(profileId: String, sourceId: String) = "$profileId.$sourceId"
}

class YoutubeAccountImportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (applicationContext.isGrayjoyForeground()) return Result.retry()
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val sourceId = inputData.getString(KEY_SOURCE_ID).orEmpty()
        if (profileId.isBlank() || sourceId.isBlank()) return Result.failure()
        setForeground(createForegroundInfo())

        val preferences = GrayjayPreferences(applicationContext, profileId)
        val repository = SharedPreferencesLibraryRepository(applicationContext, profileId)
        val sourceRepository = SharedPreferencesSourceRepository(applicationContext, profileId)
        // Media3 owns a main-looper player even though this worker only uses source import APIs.
        // Construct and release the adapter on that looper; all network/plugin import work still
        // runs on its bounded IO dispatchers.
        val engine = withContext(Dispatchers.Main.immediate) {
            AndroidGrayjayEngine(applicationContext)
        }
        return try {
            val fallbackSources = LocalContentRepository().snapshot().sources
            val sources = (engine.sources(fallbackSources) + sourceRepository.loadCustomSources())
                .distinctBy { it.engineId }
            engine.registerSources(sources)
            engine.setProfile(profileId)
            if (!engine.isSourceAuthenticated(sourceId)) return Result.success()

            val savedVideos = repository.loadSavedVideos()
            val playlists = repository.loadPlaylists()
            val knownVideosById = savedVideos.associateBy { it.id }
            val selection = EngineUserImportSelection(
                subscriptions = inputData.getBoolean(KEY_SUBSCRIPTIONS, true),
                history = inputData.getBoolean(KEY_HISTORY, true),
                playlists = inputData.getBoolean(KEY_PLAYLISTS, true),
                likedVideos = inputData.getBoolean(KEY_LIKED, true),
                knownSubscriptionUrls = preferences.loadImportedChannels()
                    .mapTo(mutableSetOf()) { it.id },
                knownHistoryVideoUrls = savedVideos.asSequence()
                    .filter { it.lastWatchedAt > 0L }
                    .mapTo(mutableSetOf()) { it.contentUrl.ifBlank { it.id } },
                knownPlaylistVideoUrls = playlists
                    .asSequence()
                    .filter { it.id.startsWith("account:") }
                    .associate { playlist ->
                        playlist.id.removePrefix("account:") to playlist.videoIds
                            .mapTo(mutableSetOf()) { videoId ->
                                knownVideosById[videoId]?.contentUrl?.ifBlank { videoId } ?: videoId
                            }
                    },
            )
            val imported = engine.importUserData(sourceId, selection) { progress ->
                setProgressAsync(
                    Data.Builder()
                        .putString(YoutubeImportScheduler.KEY_STAGE, progress.stage.name)
                        .putInt(YoutubeImportScheduler.KEY_COMPLETED, progress.completed)
                        .apply {
                            progress.total?.let {
                                putInt(YoutubeImportScheduler.KEY_TOTAL, it)
                            }
                            progress.currentItemCompleted?.let {
                                putInt(YoutubeImportScheduler.KEY_CURRENT_ITEM, it)
                            }
                        }
                        .build(),
                )
            }
            applyAccountImportTransaction(
                repository = repository,
                preferences = preferences,
                result = imported,
                repairSyntheticHistoryDates = selection.history,
            )
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { engine.release() }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.background_jobs),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.import_from_youtube))
            .setContentText(applicationContext.getString(R.string.youtube_import_background_running))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_HISTORY = "history"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_LIKED = "liked"
        private const val CHANNEL_ID = "grayjoy_background_jobs"
        private const val NOTIFICATION_ID = 22041
    }
}

private fun Context.isGrayjoyForeground(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
}
