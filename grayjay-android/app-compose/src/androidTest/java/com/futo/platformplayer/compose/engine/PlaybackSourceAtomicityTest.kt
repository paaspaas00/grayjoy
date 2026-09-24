package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.compose.ui.VideoQualityUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackSourceAtomicityTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun invalidReplacementManifestsPreserveCurrentTimelineAndQuality() {
        activity.scenario.onActivity { context ->
            val engine = AndroidGrayjayEngine(context)
            // A local silent WAV avoids network access and real library/source dependencies.
            val localAudio = "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="
            val video = VideoUiModel(
                id = "atomic-source-fixture", title = "Fixture", creator = "Fixture",
                metadata = "", duration = "", sourceId = "fixture", playbackUrl = localAudio,
                qualityVariants = listOf(
                    VideoQualityUiModel(height = 720, playbackUrl = localAudio),
                    VideoQualityUiModel(height = 1080, playbackUrl = localAudio, playbackManifest = "invalid manifest"),
                ),
            )
            val invalid = video.copy(
                id = "invalid-fixture", playbackManifest = "invalid manifest", qualityVariants = emptyList(),
            )
            try {
                engine.open(listOf(video), video.id, false)
                assertRejected { engine.setVideoQuality(1080) }
                engine.refreshProgress()
                assertNull(engine.playback.value.selectedVideoQuality)
                assertEquals(listOf(video.id), engine.playback.value.queueVideoIds)

                assertRejected { engine.appendToQueue(listOf(invalid), listOf(video.id, invalid.id)) }
                engine.refreshProgress()
                assertEquals(listOf(video.id), engine.playback.value.queueVideoIds)
                assertEquals(1, engine.player.mediaItemCount)

                assertRejected { engine.open(listOf(invalid), invalid.id, false) }
                engine.refreshProgress()
                assertEquals(listOf(video.id), engine.playback.value.queueVideoIds)
                assertEquals(video.id, engine.player.currentMediaItem?.mediaId)

                assertRejected { engine.replaceCurrent(invalid.copy(id = video.id), 0L, false) }
                // A rejected replacement must not poison later valid quality selections.
                engine.setVideoQuality(720)
                assertEquals(720, engine.playback.value.selectedVideoQuality)
                assertEquals(video.id, engine.player.currentMediaItem?.mediaId)
            } finally {
                engine.closePlayback()
                engine.release()
            }
        }
    }

    private fun assertRejected(action: () -> Unit) {
        try {
            action()
            fail("Invalid source must be rejected before replacing the working timeline")
        } catch (_: Exception) { }
    }
}
