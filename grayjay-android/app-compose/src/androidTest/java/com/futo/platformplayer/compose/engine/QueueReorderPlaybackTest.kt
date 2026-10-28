package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class QueueReorderPlaybackTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    @Test fun reordersWithoutRestartingCurrentTrackAndStopsBeforeAnUnpreparedTrack() {
        lateinit var engine: AndroidGrayjayEngine
        lateinit var file: File
        activity.scenario.onActivity { context ->
            val wave = ByteBuffer.allocate(44 + 320000).order(ByteOrder.LITTLE_ENDIAN)
            wave.put("RIFF".toByteArray()).putInt(320036).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(320000)
            file = File.createTempFile("queue-order-", ".wav", context.cacheDir).apply { writeBytes(wave.array()) }
            engine = AndroidGrayjayEngine(context)
            val videos = listOf("a", "b", "c").map { id -> VideoUiModel(id, id, "Fixture", "", "0:10",
                sourceId = "fixture", playbackUrl = file.toURI().toString(), playbackMimeType = "audio/wav") }
            engine.open(videos, "b", false)
            engine.player.seekTo(3000)
        }
        try {
            activity.scenario.onActivity {
                engine.reorderPreparedQueue(listOf("b", "c", "a"))
                assertEquals("b", engine.player.currentMediaItem?.mediaId)
                assertEquals(3000L, engine.player.currentPosition)
                assertFalse(engine.player.playWhenReady)
                assertEquals(listOf("b", "c", "a"), engine.playback.value.queueVideoIds)
                engine.reorderPreparedQueue(listOf("b", "missing", "a", "c"))
                assertEquals(listOf("b"), engine.playback.value.queueVideoIds)
                assertEquals(3000L, engine.player.currentPosition)
                engine.reorderPreparedQueue(listOf("unrelated"))
                assertEquals("b", engine.player.currentMediaItem?.mediaId)
            }
            activity.scenario.onActivity {
                engine.setMusicPlaybackMode(true)
                engine.setVideoQuality(2160)
                assertEquals(480, engine.player.trackSelectionParameters.maxVideoHeight)
                assertTrue(engine.player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO))
                engine.setMusicPlaybackMode(false)
                engine.setVideoQuality(1080)
                assertEquals(1080, engine.player.trackSelectionParameters.maxVideoHeight)
                assertFalse(engine.player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO))
            }
        } finally { activity.scenario.onActivity { engine.release(); file.delete() } }
    }
}
