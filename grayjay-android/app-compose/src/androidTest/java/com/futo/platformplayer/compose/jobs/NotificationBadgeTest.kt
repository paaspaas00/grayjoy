package com.futo.platformplayer.compose.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.notifications.ensureNonBadgingChannel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NotificationBadgeTest {
    @Test fun migrationDisablesBadgesWithoutReenablingBlockedNotifications() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val old = "test-old-${UUID.randomUUID()}"; val new = "test-new-${UUID.randomUUID()}"
        try {
            manager.createNotificationChannel(NotificationChannel(old, "Test", NotificationManager.IMPORTANCE_NONE).apply { setShowBadge(true) })
            ensureNonBadgingChannel(context, new, old, "Test")
            val channel = manager.getNotificationChannel(new)
            assertFalse(channel.canShowBadge()); assertEquals(NotificationManager.IMPORTANCE_NONE, channel.importance)
            assertTrue(manager.getNotificationChannel(old).canShowBadge())
        } finally { manager.deleteNotificationChannel(old); manager.deleteNotificationChannel(new) }
    }
}
