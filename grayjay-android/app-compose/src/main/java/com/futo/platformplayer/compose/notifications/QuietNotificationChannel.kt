package com.futo.platformplayer.compose.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

internal fun migratedNotificationVisibility(context: Context, channelId: String, legacyId: String, fallback: Int): Int {
    val manager = context.getSystemService(NotificationManager::class.java)
    // Respect an explicit lock-screen choice on the new channel, or carry the previous
    // channel's restriction into the notification itself (apps cannot migrate system-only flags).
    return manager.getNotificationChannel(channelId)?.lockscreenVisibility?.takeIf { it in -1..1 }
        ?: manager.getNotificationChannel(legacyId)?.lockscreenVisibility?.takeIf { it in -1..1 }
        ?: fallback
}

/** Badging is immutable after channel creation. Migrate without re-enabling user-blocked alerts. */
internal fun ensureNonBadgingChannel(context: Context, id: String, legacyId: String, name: String, description: String? = null) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(id) != null) return
    val previous = manager.getNotificationChannel(legacyId)
    manager.createNotificationChannel(NotificationChannel(id, name,
        previous?.importance ?: NotificationManager.IMPORTANCE_LOW).apply {
        this.description = description
        setShowBadge(false)
        setSound(previous?.sound, previous?.audioAttributes)
        enableVibration(previous?.shouldVibrate() == true)
        previous?.vibrationPattern?.let { vibrationPattern = it }
        enableLights(previous?.shouldShowLights() == true)
        previous?.let { lightColor = it.lightColor; group = it.group }
    })
}
