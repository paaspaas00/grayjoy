package com.futo.platformplayer.compose.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.futo.platformplayer.compose.MainActivity
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.downloads.GrayjoyDownloadStore

/** Keeps user-started preparation/import work alive and mirrors its aggregate progress. */
class GrayjoyActiveJobsService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_UPDATE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0)?.coerceIn(0, 100) ?: 0
        val jobCount = intent?.getIntExtra(EXTRA_JOB_COUNT, 1)?.coerceAtLeast(1) ?: 1
        val description = intent?.getStringExtra(EXTRA_DESCRIPTION)
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.jobs_running)
        val notification = buildActiveJobsNotification(
            context = this,
            jobCount = jobCount,
            progress = progress,
            description = description,
        )
        try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        } catch (error: RuntimeException) {
            // Permission/time-budget can change between startForegroundService and delivery.
            // A failure here used to escape on the main thread and crash the entire app.
            Log.w("GrayjoyActiveJobs", "Could not promote the job service", error)
            interruptJobs()
            stopSelf()
        }
        foregroundStartPending = false
        if (stopRequested) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        // The state belongs to the real jobs in the ViewModel/WorkManager. Restarting this
        // service alone after a crash would create a phantom "1 job, 0%" notification.
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        interruptJobs()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        interruptJobs()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun interruptJobs() {
        RunningJobs.pause()
        GrayjoyDownloadStore.holdUntilAppRestore(this)
        GrayjoyDownloadStore.pauseIfCreated()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_jobs),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.background_jobs_notification_description)
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "grayjoy_active_jobs"
        private const val NOTIFICATION_ID = 2050
        private const val ACTION_UPDATE =
            "com.futo.platformplayer.compose.jobs.UPDATE"
        private const val EXTRA_JOB_COUNT = "job_count"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_DESCRIPTION = "description"
        private var foregroundStartPending = false
        private var stopRequested = false

        fun update(context: Context, jobCount: Int, progress: Float, description: String) {
            stopRequested = false
            val intent = Intent(context, GrayjoyActiveJobsService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_JOB_COUNT, jobCount.coerceAtLeast(1))
                .putExtra(EXTRA_PROGRESS, (progress.coerceIn(0f, 1f) * 100f).toInt())
                .putExtra(EXTRA_DESCRIPTION, description)
            val alreadyPending = foregroundStartPending
            foregroundStartPending = true
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { error ->
                    foregroundStartPending = alreadyPending
                    Log.w("GrayjoyActiveJobs", "Could not start the job service", error)
                    RunningJobs.pause()
                }
        }

        fun stop(context: Context) {
            stopRequested = true
            if (!foregroundStartPending) {
                context.stopService(Intent(context, GrayjoyActiveJobsService::class.java))
            }
        }
    }
}

internal fun buildActiveJobsNotification(
    context: Context,
    jobCount: Int,
    progress: Int,
    description: String,
) = NotificationCompat.Builder(context, "grayjoy_active_jobs")
    .setSmallIcon(android.R.drawable.stat_sys_download)
    .setContentTitle(
        context.resources.getQuantityString(
            R.plurals.active_jobs,
            jobCount.coerceAtLeast(1),
            jobCount.coerceAtLeast(1),
        ),
    )
    .setContentText(description)
    .setSubText(context.getString(R.string.percent_complete, progress.coerceIn(0, 100)))
    .setContentIntent(
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )
    .setProgress(100, progress.coerceIn(0, 100), false)
    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    .setOnlyAlertOnce(true)
    .setOngoing(true)
    .build()
