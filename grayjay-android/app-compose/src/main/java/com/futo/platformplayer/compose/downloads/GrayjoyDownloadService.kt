package com.futo.platformplayer.compose.downloads

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import com.futo.platformplayer.compose.MainActivity
import com.futo.platformplayer.compose.R

@OptIn(UnstableApi::class)
class GrayjoyDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_notification_channel,
    0,
) {
    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }
    private val scheduler by lazy { PlatformScheduler(this, DOWNLOAD_JOB_ID) }

    override fun getDownloadManager(): DownloadManager =
        GrayjoyDownloadStore.get(this).downloadManager

    // Lets Android restart the service when network requirements become satisfied, even if the
    // app process was reclaimed while the transfer was waiting offline.
    override fun getScheduler(): Scheduler = scheduler

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification = notificationHelper.buildProgressNotification(
        this,
        android.R.drawable.stat_sys_download,
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
        getString(R.string.downloading_for_offline_use),
        downloads,
        notMetRequirements,
    )

    companion object {
        private const val NOTIFICATION_ID = 2042
        private const val DOWNLOAD_JOB_ID = 2043
        private const val CHANNEL_ID = "grayjoy_downloads"

        fun add(context: Context, request: DownloadRequest) {
            try {
                sendAddDownload(context, GrayjoyDownloadService::class.java, request, true)
            } catch (_: IllegalStateException) {
                GrayjoyDownloadStore.get(context).downloadManager.addDownload(request)
            }
        }

        fun remove(context: Context, id: String) {
            GrayjoyDownloadStore.get(context).downloadManager.removeDownload(id)
        }
    }
}
