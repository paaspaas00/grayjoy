package com.futo.platformplayer.compose.jobs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.Assert.assertTrue
import com.futo.platformplayer.compose.ui.*
import com.futo.platformplayer.compose.downloads.DownloadBatchProgress
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
        assertEquals("grayjoy_background_jobs", notification.group)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test fun aggregateNotificationCountsCompletedVideosAsPartOfTheBatch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rows = (1..4).associate { "$it" to DownloadUiModel("p", "$it", status =
            if (it == 1) DownloadStatus.Completed else DownloadStatus.Queued) }
        val snapshot = activeJobsSnapshot(context, GrayjayUiState(downloads = rows,
            downloadBatchProgress = DownloadBatchProgress(4, 1, .25f)), null)
        assertEquals(1, snapshot.count)
        assertEquals(25, snapshot.percent)
        assertTrue(snapshot.description.contains("1/4"))
    }
}
