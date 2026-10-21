package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class EndedPlaybackToggleTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun pressingPlayAfterNaturalEndRestartsInsteadOfPausingAtZero() {
        lateinit var engine: AndroidGrayjayEngine
        lateinit var file: File
        val starts = AtomicInteger()
        activity.scenario.onActivity { context ->
            val wave = ByteBuffer.allocate(44 + 32000).order(ByteOrder.LITTLE_ENDIAN)
            wave.put("RIFF".toByteArray()).putInt(32036).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(32000)
            file = File.createTempFile("ended-playback-", ".wav", context.cacheDir).apply { writeBytes(wave.array()) }
            engine = AndroidGrayjayEngine(context)
            engine.player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) starts.incrementAndGet() }
            })
            val video = VideoUiModel("ended-fixture", "Fixture", "Fixture", "", "0:01",
                sourceId = "fixture", playbackUrl = file.toURI().toString(), playbackMimeType = "audio/wav")
            engine.open(listOf(video), video.id, true)
        }
        try {
            waitUntil { var ended = false; activity.scenario.onActivity { ended = engine.player.playbackState == Player.STATE_ENDED }; ended }
            val before = starts.get()
            activity.scenario.onActivity {
                assertTrue(engine.player.playWhenReady)
                engine.togglePlayback()
            }
            waitUntil { starts.get() > before }
        } finally { activity.scenario.onActivity { engine.release(); file.delete() } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000L
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50L)
        assertTrue("Playback did not reach the expected state", condition())
    }
}
