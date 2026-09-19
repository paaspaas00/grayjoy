package com.futo.platformplayer.compose.jobs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveJobsNotificationTest {
    @Test
    fun foregroundNotificationContainsAggregateProgress() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = buildActiveJobsNotification(
            context = context,
            jobCount = 3,
            progress = 42,
            description = "3 downloads",
        )
        assertNotNull(notification)
        assertEquals(42, notification.extras.getInt("android.progress"))
        assertEquals(100, notification.extras.getInt("android.progressMax"))
        assertEquals("42%", notification.extras.getString("android.subText"))
    }
}
