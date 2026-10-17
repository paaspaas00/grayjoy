package com.futo.platformplayer.compose.engine

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.backend.YoutubeSubscriptionFetchMode
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.SearchContentType
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Live playback probe using its own engine, without touching profiles or saved libraries. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LowLevelPlaybackProbeTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    @Test fun reportedVideoInTheCompleteApp(): Unit = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = androidx.test.core.app.ActivityScenario.launch<com.futo.platformplayer.compose.MainActivity>(
            android.content.Intent(context, com.futo.platformplayer.compose.MainActivity::class.java).apply {
                action = android.content.Intent.ACTION_VIEW
                data = android.net.Uri.parse("https://www.youtube.com/watch?v=qFEuYUfauNY")
            })
        lateinit var model: com.futo.platformplayer.compose.GrayjayViewModel
        scenario.onActivity { model = (it.application as com.futo.platformplayer.compose.GrayjoyApplication).playbackViewModel }
        var previousDropped = 0
        var largestDroppedBurst = 0
        try {
            withTimeout(90_000) {
                while (!withContext(Dispatchers.Main) { model.player.isPlaying &&
                    model.player.mediaMetadata.title?.contains("Viral", true) == true }) delay(100)
            }
            repeat(14) { index ->
                delay(5_000)
                withContext(Dispatchers.Main) {
                    val player = model.player as androidx.media3.exoplayer.ExoPlayer
                    val counters = player.videoDecoderCounters
                    counters?.ensureUpdated()
                    val totalDropped = counters?.droppedBufferCount ?: 0
                    if (index >= 2) largestDroppedBurst = maxOf(largestDroppedBurst, totalDropped - previousDropped)
                    previousDropped = totalDropped
                    Log.i("LowLevelProbe", "full-app=$index pos=${player.currentPosition} buffer=${player.bufferedPosition} " +
                        "format=${player.videoFormat} rendered=${counters?.renderedOutputBufferCount} " +
                        "dropped=${counters?.droppedBufferCount} state=${player.playbackState} quality=${model.uiState.value.preferredVideoQuality}")
                    assertNull(model.uiState.value.playback.errorMessage)
                }
            }
            assertTrue("Sustained frame loss during playback: $largestDroppedBurst frames in five seconds", largestDroppedBurst < 15)
        } finally {
            withContext(Dispatchers.Main) { model.closePlayback() }
            scenario.close()
        }
    }
    @Test fun reportedVideoRetainsFramesAndProgressForSeventySeconds(): Unit = runBlocking {
        lateinit var engine: AndroidGrayjayEngine
        var dropped = 0
        var buffering = 0
        activity.scenario.onActivity { context ->
            engine = AndroidGrayjayEngine(context)
            engine.player.volume = 0f
            engine.configureYoutubeBackend(true, YoutubeSubscriptionFetchMode.Fast)
            context.setContentView((context.layoutInflater.inflate(R.layout.view_compose_player, null) as PlayerView).apply {
                player = engine.player
                keepScreenOn = true
            })
            (engine.player as androidx.media3.exoplayer.ExoPlayer).addAnalyticsListener(object : AnalyticsListener {
                override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                    dropped += droppedFrames
                    Log.i("LowLevelProbe", "dropped=$droppedFrames elapsed=$elapsedMs total=$dropped")
                }
                override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                    if (state == Player.STATE_BUFFERING) buffering++
                    Log.i("LowLevelProbe", "state=$state position=${eventTime.currentPlaybackPositionMs}")
                }
            })
        }
        try {
            val results = withTimeout(90_000) { engine.search("Low Level I tested the viral reverse engineering", setOf("youtube"),
                SearchCorpus(emptyList(), emptyList(), emptyList()), SearchContentType.Videos).videos }
            val video = results.firstOrNull { it.title.contains("viral", true) && it.creator.contains("Low Level", true) }
                ?: error("Reported video not found: ${results.take(5).map { it.title }}")
            Log.i("LowLevelProbe", "video=${video.contentUrl} title=${video.title}")
            val source = withTimeout(90_000) { engine.resolve(video) }
            withContext(Dispatchers.Main) { engine.open(listOf(source), source.id, true) }
            withTimeout(45_000) {
                while (!withContext(Dispatchers.Main) { engine.player.isPlaying }) delay(100)
            }
            repeat(14) { index ->
                delay(5_000)
                withContext(Dispatchers.Main) {
                    val player = engine.player as androidx.media3.exoplayer.ExoPlayer
                    val format = player.videoFormat
                    val counters = player.videoDecoderCounters
                    counters?.ensureUpdated()
                    Log.i("LowLevelProbe", "sample=$index pos=${player.currentPosition} buffer=${player.bufferedPosition} " +
                        "format=${format?.sampleMimeType} ${format?.width}x${format?.height}@${format?.frameRate} " +
                        "rendered=${counters?.renderedOutputBufferCount} dropped=${counters?.droppedBufferCount} " +
                        "processingUs=${counters?.totalVideoFrameProcessingOffsetUs} buffers=$buffering")
                    assertNull(engine.playback.value.errorMessage)
                }
            }
            assertTrue(withContext(Dispatchers.Main) { engine.player.currentPosition > 55_000 })
        } finally { withContext(Dispatchers.Main) { engine.release() } }
    }
}
