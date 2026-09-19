package com.futo.platformplayer.compose.background

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.futo.platformplayer.compose.ui.YoutubeImportInterval
import com.futo.platformplayer.compose.ui.YoutubeImportScheduleUiState
import com.futo.platformplayer.compose.ui.YoutubeImportSelection
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YoutubeImportSchedulerTest {
    @Test
    fun periodicImportPersistsSelectionAndQueuesConstrainedWork() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = "schedule-test-${UUID.randomUUID()}"
        val selection = YoutubeImportSelection(
            subscriptions = true,
            history = false,
            playlists = true,
            likedVideos = false,
        )
        val state = YoutubeImportScheduleUiState(
            interval = YoutubeImportInterval.SixHours,
            selection = selection,
        )
        try {
            YoutubeImportScheduler.update(context, profile, state)
            assertEquals(state, YoutubeImportScheduler.scheduleFor(context, profile))
            val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(
                YoutubeImportScheduler.uniqueWorkName(profile, "youtube"),
            ).get()
            assertTrue(infos.single().state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED))
        } finally {
            YoutubeImportScheduler.clearProfile(context, profile)
        }
    }
}
