package com.futo.platformplayer.compose.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PictureInPictureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_PLAYBACK) {
            PlaybackNotificationService.toggleAttachedPlayback()
        }
    }

    companion object {
        private const val ACTION_TOGGLE_PLAYBACK =
            "com.futo.platformplayer.compose.action.TOGGLE_PIP_PLAYBACK"
        private const val REQUEST_TOGGLE_PLAYBACK = 4_211

        fun togglePlaybackIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE_PLAYBACK,
            Intent(context, PictureInPictureActionReceiver::class.java).apply {
                action = ACTION_TOGGLE_PLAYBACK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
