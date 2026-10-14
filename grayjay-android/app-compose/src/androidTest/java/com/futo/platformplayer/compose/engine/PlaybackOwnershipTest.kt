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

    @Test fun resolverEngineDoesNotAdvertiseAnEmptyMediaSession() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        try {
            activity.scenario.onActivity { context ->
                val manager = context.getSystemService(android.media.session.MediaSessionManager::class.java)
                fun ownSessions() = manager.getActiveSessions(null).count { it.packageName == context.packageName }
                val before = ownSessions()
                val worker = AndroidGrayjayEngine(context)
                try { assertEquals(before, ownSessions()) } finally { worker.release() }
                assertEquals(before, ownSessions())
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    @Test fun playbackWaitsForItsNotificationAndPausesIfTheHostDisappears() {
        lateinit var engine: AndroidGrayjayEngine
        lateinit var fixture: java.io.File
        var started = false
        var hostedAtStart = false
        var prompt: android.media.AudioTrack? = null
        activity.scenario.onActivity { context ->
            fixture = java.io.File(context.cacheDir, "voice-playback-fixture.wav")
            val data = java.nio.ByteBuffer.allocate(44 + 32000).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(32036).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(32000)
            fixture.writeBytes(data.array())
            engine = AndroidGrayjayEngine(context)
            engine.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            engine.player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        hostedAtStart = PlaybackNotificationService.hosts(engine.player)
                        started = true
                    }
                }
            })
            val video = com.futo.platformplayer.compose.ui.VideoUiModel(
                id = "voice-playback-fixture", title = "Playback fixture", creator = "Fixture artist",
                metadata = "", duration = "0:01", playbackUrl = fixture.toURI().toString(), playbackMimeType = "audio/wav")
            engine.open(listOf(video), video.id, true)
        }
        try {
            val limit = android.os.SystemClock.elapsedRealtime() + 10_000
            while (!started && android.os.SystemClock.elapsedRealtime() < limit) {
                Thread.sleep(100)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            assertTrue("The hosted player did not start", started)
            assertTrue("Audio started without its notification host", hostedAtStart)
            activity.scenario.onActivity {
                engine.setOtherAudioDucking(true, 35)
                engine.setVoiceListeningVolume("volume-fixture", 50)
                prompt = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(16000)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC).setBufferSizeInBytes(32000).build().also { track ->
                        track.write(ShortArray(16000), 0, 16000)
                        track.setLoopPoints(0, 16000, -1); track.play()
                    }
            }
            Thread.sleep(300)
            activity.scenario.onActivity {
                assertEquals(.5f, engine.player.volume, .005f)
                engine.setVoiceListeningVolume("volume-fixture", null)
            }
            // The prompt deliberately remains active to simulate a delayed Bluetooth drain.
            repeat(8) {
                Thread.sleep(200)
                activity.scenario.onActivity { assertEquals(1f, engine.player.volume, .005f) }
            }
            activity.scenario.onActivity { context ->
                engine.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                assertEquals("Playback fixture", engine.player.mediaMetadata.title.toString())
                context.stopService(android.content.Intent(context, PlaybackNotificationService::class.java))
            }
            Thread.sleep(400)
            activity.scenario.onActivity { assertFalse(engine.player.playWhenReady) }
        } finally { activity.scenario.onActivity {
            prompt?.let { track -> runCatching { track.stop() }; track.release() }
            engine.release(); fixture.delete()
        } }
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
