package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.futo.platformplayer.compose.playback.PlaybackNotificationService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackOwnershipTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun secondaryEngineCannotDismissMainPlaybackNotification() {
        activity.scenario.onActivity { context ->
            val main = ExoPlayer.Builder(context).build()
            val worker = ExoPlayer.Builder(context).build()
            val session = MediaSession.Builder(context, main).setId("ownership-test").build()
            try {
                PlaybackNotificationService.show(context, main, session, {})
                assertFalse(PlaybackNotificationService.dismiss(context, worker))
                assertTrue(PlaybackNotificationService.dismiss(context, main))
                assertFalse(PlaybackNotificationService.dismiss(context, main))
            } finally {
                session.release()
                worker.release()
                main.release()
            }
        }
        // Drain service callbacks: the immediate show/dismiss pair must still acknowledge the
        // foreground start rather than causing ForegroundServiceDidNotStartInTimeException.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(6_000)
    }

    @Test fun immediatelyCompletedJobStillAcknowledgesForegroundStart() {
        activity.scenario.onActivity { context ->
            com.futo.platformplayer.compose.jobs.GrayjoyActiveJobsService.update(
                context, 1, 0.5f, "Fixture job",
            )
            com.futo.platformplayer.compose.jobs.GrayjoyActiveJobsService.stop(context)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(6_000)
    }
}
