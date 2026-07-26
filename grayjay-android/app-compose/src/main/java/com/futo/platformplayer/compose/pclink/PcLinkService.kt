package com.futo.platformplayer.compose.pclink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.futo.platformplayer.compose.MainActivity
import com.futo.platformplayer.compose.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PcLinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var manager: PcLinkManager
    private var server: PcLinkHttpServer? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        manager = PcLinkManager.get(this)
        createNotificationChannel()
        startForegroundCompat(buildNotification(manager.snapshot.value.activePlayback))
        server = PcLinkHttpServer(manager).also { http ->
            runCatching(http::start).onFailure {
                stopSelf()
                return
            }
        }
        notificationJob = scope.launch {
            manager.snapshot.collectLatest { snapshot ->
                if (snapshot.pairedComputers.isEmpty()) {
                    stopSelf()
                } else {
                    notify(snapshot.activePlayback)
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(3_000L)
                manager.expireStalePlaybacks()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> command(intent, PcRemoteCommandType.Play)
            ACTION_PAUSE -> command(intent, PcRemoteCommandType.Pause)
            ACTION_TOGGLE -> command(intent, PcRemoteCommandType.Toggle)
            ACTION_PREVIOUS -> command(intent, PcRemoteCommandType.Previous)
            ACTION_NEXT -> command(intent, PcRemoteCommandType.Next)
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        server?.stop()
        server = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun command(intent: Intent, type: PcRemoteCommandType) {
        intent.getStringExtra(EXTRA_COMPUTER_ID)
            ?.let { manager.enqueueCommand(it, type) }
    }

    private fun notify(playback: PcPlaybackState?) {
        try {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(playback),
            )
        } catch (_: SecurityException) {
            // Android still keeps the foreground-service entry visible in Task Manager when the
            // user denies notification permission. The LAN link must keep working either way.
        }
    }

    private fun buildNotification(playback: PcPlaybackState?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_playback)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (playback == null) {
            return builder
                .setContentTitle(getString(R.string.pc_link_ready))
                .setContentText(getString(R.string.pc_link_waiting))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        val title = playback.videoTitle.ifBlank { playback.title }
        builder
            .setContentTitle(getString(R.string.on_pc_with_name, playback.computerName))
            .setContentText(title)
            .setContentInfo(
                getString(
                    if (playback.isPlaying) R.string.now_playing else R.string.download_paused,
                ),
            )
            .setSubText(
                if (playback.kind == PcMediaKind.Playlist) playback.title.takeIf { it != title }
                else null,
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.previous_video),
                commandIntent(ACTION_PREVIOUS, playback.computerId, 1),
            )
            .addAction(
                if (playback.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                getString(if (playback.isPlaying) R.string.pause else R.string.play),
                commandIntent(
                    if (playback.isPlaying) ACTION_PAUSE else ACTION_PLAY,
                    playback.computerId,
                    2,
                ),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.next_video),
                commandIntent(ACTION_NEXT, playback.computerId, 3),
            )
        if (playback.durationMs > 0L) {
            builder.setProgress(
                playback.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                playback.positionMs.coerceIn(0L, playback.durationMs)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                false,
            )
        }
        return builder.build()
    }

    private fun commandIntent(
        action: String,
        computerId: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, PcLinkService::class.java)
            .setAction(action)
            .putExtra(EXTRA_COMPUTER_ID, computerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pc_link_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.pc_link_notification_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "grayjoy_pc_link"
        private const val NOTIFICATION_ID = 7304
        private const val ACTION_PLAY = "com.futo.platformplayer.compose.pclink.PLAY"
        private const val ACTION_PAUSE = "com.futo.platformplayer.compose.pclink.PAUSE"
        private const val ACTION_TOGGLE = "com.futo.platformplayer.compose.pclink.TOGGLE"
        private const val ACTION_PREVIOUS = "com.futo.platformplayer.compose.pclink.PREVIOUS"
        private const val ACTION_NEXT = "com.futo.platformplayer.compose.pclink.NEXT"
        private const val ACTION_STOP = "com.futo.platformplayer.compose.pclink.STOP"
        private const val EXTRA_COMPUTER_ID = "computer_id"

        fun ensureRunning(context: Context) {
            val manager = PcLinkManager.get(context)
            if (manager.snapshot.value.pairedComputers.isEmpty()) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, PcLinkService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PcLinkService::class.java))
        }
    }
}
