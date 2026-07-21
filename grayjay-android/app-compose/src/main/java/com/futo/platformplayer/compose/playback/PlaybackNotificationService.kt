package com.futo.platformplayer.compose.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.R as Media3UiR
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.compose.MainActivity
import com.futo.platformplayer.compose.R

/**
 * Hosts the notification for the exact Player used by the Compose UI. Keeping a
 * single player makes notification actions, the mini-player, and Now Playing
 * remain in lockstep.
 */
@UnstableApi
class PlaybackNotificationService : Service() {
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var descriptionAdapter: DescriptionAdapter
    private var closingFromNotification = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        descriptionAdapter = DescriptionAdapter(this)
        val notificationListener = object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationPosted(
                        notificationId: Int,
                        notification: Notification,
                        ongoing: Boolean,
                    ) {
                        // A paused player is still an active playback session. Detaching the
                        // foreground service here lets Android kill it after the screen locks,
                        // which also loses the player and queue. Keep the service foreground
                        // until the user explicitly dismisses/closes playback.
                        startForegroundCompat(notificationId, notification)
                    }

            override fun onNotificationCancelled(
                notificationId: Int,
                dismissedByUser: Boolean,
            ) {
                if (dismissedByUser) {
                    closeFromNotification()
                } else if (attachment != null && !closingFromNotification) {
                    // Media3 can briefly cancel/recreate its notification during state or
                    // metadata changes. That is not the same as the user swiping it away.
                    startForegroundCompat(NOTIFICATION_ID, bootstrapNotification())
                    playerNotificationManager.invalidate()
                }
            }
        }
        val closeActionReceiver = CloseActionReceiver(this)
        playerNotificationManager = CompactClosePlayerNotificationManager(
            context = this,
            descriptionAdapter = descriptionAdapter,
            notificationListener = notificationListener,
            closeActionReceiver = closeActionReceiver,
        )
            .apply {
                setUsePlayPauseActions(true)
                setUsePreviousAction(true)
                setUsePreviousActionInCompactView(true)
                setUseNextAction(true)
                setUseNextActionInCompactView(true)
                setUseRewindAction(false)
                setUseFastForwardAction(false)
                setUseStopAction(false)
                setUseChronometer(true)
                setColorized(true)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setPriority(NotificationCompat.PRIORITY_LOW)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground-service start must be acknowledged even if the user closes playback in
        // the small window between startForegroundService() and this callback. Posting the
        // bootstrap first makes that race legal on Android 12+; we can remove it immediately when
        // there is no longer an attachment.
        startForegroundCompat(NOTIFICATION_ID, bootstrapNotification())
        val playback = attachment
        if (playback == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        playerNotificationManager.setMediaSessionToken(playback.mediaSession.platformToken)
        playerNotificationManager.setPlayer(playback.player)
        playerNotificationManager.invalidate()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        descriptionAdapter.clearArtworkRequest()
        playerNotificationManager.setPlayer(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun closeFromNotification() {
        if (closingFromNotification) return
        closingFromNotification = true
        val closePlayback = attachment?.closePlayback
        attachment = null
        playerNotificationManager.setPlayer(null)
        closePlayback?.invoke()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.playback_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun bootstrapNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_playback)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.preparing_playback))
        .setContentIntent(contentIntent())
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun startForegroundCompat(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private class DescriptionAdapter(
        private val service: PlaybackNotificationService,
    ) : PlayerNotificationManager.MediaDescriptionAdapter {
        private var artworkTarget: CustomTarget<Bitmap>? = null

        override fun getCurrentContentTitle(player: Player): CharSequence =
            player.mediaMetadata.title?.takeIf(CharSequence::isNotBlank)
                ?: service.getString(R.string.app_name)

        override fun getCurrentContentText(player: Player): CharSequence =
            player.mediaMetadata.artist ?: ""

        override fun createCurrentContentIntent(player: Player): PendingIntent =
            service.contentIntent()

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback,
        ): Bitmap? {
            clearArtworkRequest()
            val artworkUri = player.mediaMetadata.artworkUri ?: return null
            val requestedMediaId = player.currentMediaItem?.mediaId
            artworkTarget = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?,
                ) {
                    if (attachment?.player?.currentMediaItem?.mediaId == requestedMediaId) {
                        callback.onBitmap(resource)
                    }
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) = Unit
            }.also { target ->
                Glide.with(service).asBitmap().load(artworkUri).into(target)
            }
            return null
        }

        fun clearArtworkRequest() {
            artworkTarget?.let { Glide.with(service).clear(it) }
            artworkTarget = null
        }
    }

    /** Keeps the stop/X action visible on Pixel-style compact media cards. */
    @Suppress("DEPRECATION")
    private class CompactClosePlayerNotificationManager(
        context: Context,
        descriptionAdapter: PlayerNotificationManager.MediaDescriptionAdapter,
        notificationListener: PlayerNotificationManager.NotificationListener,
        closeActionReceiver: PlayerNotificationManager.CustomActionReceiver,
    ) : PlayerNotificationManager(
        context,
        CHANNEL_ID,
        NOTIFICATION_ID,
        descriptionAdapter,
        notificationListener,
        closeActionReceiver,
        R.drawable.ic_notification_playback,
        Media3UiR.drawable.exo_notification_rewind,
        Media3UiR.drawable.exo_notification_play,
        Media3UiR.drawable.exo_notification_pause,
        R.drawable.ic_notification_close,
        Media3UiR.drawable.exo_notification_fastforward,
        Media3UiR.drawable.exo_notification_previous,
        Media3UiR.drawable.exo_notification_next,
        null,
    ) {
        override fun getActionIndicesForCompactView(
            actionNames: List<String>,
            player: Player,
        ): IntArray = buildList {
            listOf(ACTION_PREVIOUS, ACTION_NEXT, ACTION_CLOSE_NOTIFICATION).forEach { action ->
                actionNames.indexOf(action)
                    .takeIf { it >= 0 }
                    ?.let(::add)
            }
        }.take(3).toIntArray()
    }

    private class CloseActionReceiver(
        private val service: PlaybackNotificationService,
    ) : PlayerNotificationManager.CustomActionReceiver {
        override fun createCustomActions(
            context: Context,
            instanceId: Int,
        ): Map<String, NotificationCompat.Action> = mapOf(
            ACTION_CLOSE_NOTIFICATION to NotificationCompat.Action.Builder(
                R.drawable.ic_notification_close,
                context.getString(R.string.close_playback),
                PendingIntent.getBroadcast(
                    context,
                    NOTIFICATION_ID + instanceId,
                    Intent(ACTION_CLOSE_NOTIFICATION)
                        .setPackage(context.packageName)
                        .putExtra(PlayerNotificationManager.EXTRA_INSTANCE_ID, instanceId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )

        override fun getCustomActions(player: Player): List<String> =
            listOf(ACTION_CLOSE_NOTIFICATION)

        override fun onCustomAction(player: Player, action: String, intent: Intent) {
            if (action == ACTION_CLOSE_NOTIFICATION) service.closeFromNotification()
        }
    }

    private data class Attachment(
        val player: Player,
        val mediaSession: MediaSession,
        val closePlayback: () -> Unit,
    )

    companion object {
        private const val CHANNEL_ID = "grayjay_playback"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_CLOSE_NOTIFICATION =
            "com.futo.platformplayer.compose.action.CLOSE_PLAYBACK_NOTIFICATION"

        @Volatile
        private var attachment: Attachment? = null

        fun show(
            context: Context,
            player: Player,
            mediaSession: MediaSession,
            closePlayback: () -> Unit,
        ) {
            attachment = Attachment(player, mediaSession, closePlayback)
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackNotificationService::class.java),
            )
        }

        fun dismiss(context: Context) {
            attachment = null
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            context.stopService(Intent(context, PlaybackNotificationService::class.java))
        }
    }
}
