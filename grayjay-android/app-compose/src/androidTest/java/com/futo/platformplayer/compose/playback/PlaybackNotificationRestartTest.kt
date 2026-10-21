package com.futo.platformplayer.compose.playback

import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackNotificationRestartTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun immediateDismissAndShowCannotAttachNewPlaybackToTheDyingService() {
        lateinit var player: ExoPlayer
        lateinit var session: MediaSession
        lateinit var fixture: File
        activity.scenario.onActivity { context ->
            fixture = File(context.cacheDir, "restart-fixture-${UUID.randomUUID()}.wav")
            val bytes = ByteBuffer.allocate(44 + 32000).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(32036).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(32000)
            fixture.writeBytes(bytes.array())
            player = ExoPlayer.Builder(context).build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(fixture.toURI().toString()))
                prepare()
            }
            session = MediaSession.Builder(context, player).setId("restart-${UUID.randomUUID()}").build()
            PlaybackNotificationService.show(context, player, session, {}, onReady = { player.play() })
        }
        fun waitForHostedPlayback() {
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000L
            var ready = false
            while (!ready && android.os.SystemClock.elapsedRealtime() < deadline) {
                activity.scenario.onActivity { ready = PlaybackNotificationService.hosts(player) && player.isPlaying }
                if (!ready) Thread.sleep(50L)
            }
            assertTrue("Playback never acquired its replacement notification service", ready)
        }
        try {
            waitForHostedPlayback()
            repeat(4) {
                activity.scenario.onActivity { context ->
                    assertTrue(PlaybackNotificationService.dismiss(context, player))
                    PlaybackNotificationService.show(context, player, session, {}, onReady = { player.play() })
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(250L)
                waitForHostedPlayback()
            }
        } finally {
            activity.scenario.onActivity { context ->
                PlaybackNotificationService.dismiss(context, player)
                session.release()
                player.release()
                fixture.delete()
            }
        }
    }
}
