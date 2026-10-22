package com.futo.platformplayer.compose

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.graphics.SurfaceTexture
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit live probe, with the same real video and quality preference as the reported case. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReportedBreadPlaybackTest {
    @Test fun playbackKeepsFramesAndClockInSync(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("breadLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://www.youtube.com/watch?v=3fILFHNoAic")))
        lateinit var model: GrayjayViewModel
        scenario.onActivity { model = (it.application as GrayjoyApplication).playbackViewModel }
        val oldQuality = model.uiState.value.preferredVideoQuality
        val oldVolume = withContext(Dispatchers.Main) { model.player.volume }
        var largestDrop = 0
        var previousDropped = 0
        var textureFrames = 0
        var textureLongGaps = 0
        var textureMaxGapMs = 0L
        var textureLastNs = 0L
        var texture: TextureView? = null
        var originalListener: TextureView.SurfaceTextureListener? = null
        try {
            withContext(Dispatchers.Main) { model.player.volume = 0f; model.setPreferredVideoQuality(2160) }
            withTimeout(90_000) {
                while (!withContext(Dispatchers.Main) { model.player.isPlaying && model.player.duration > 300_000 }) delay(100)
            }
            scenario.onActivity { activity ->
                fun find(view: View): TextureView? {
                    if (view is TextureView) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                texture = find(activity.window.decorView)
                originalListener = texture?.surfaceTextureListener
                texture?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { originalListener?.onSurfaceTextureAvailable(s, w, h) }
                    override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) { originalListener?.onSurfaceTextureSizeChanged(s, w, h) }
                    override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = originalListener?.onSurfaceTextureDestroyed(s) ?: true
                    override fun onSurfaceTextureUpdated(s: SurfaceTexture) {
                        val now = System.nanoTime()
                        if (textureLastNs > 0) {
                            val gap = (now - textureLastNs) / 1_000_000
                            textureMaxGapMs = maxOf(textureMaxGapMs, gap)
                            if (gap > 100) textureLongGaps++
                        }
                        textureLastNs = now
                        textureFrames++
                        originalListener?.onSurfaceTextureUpdated(s)
                    }
                }
            }
            repeat(18) { sample ->
                delay(5_000)
                withContext(Dispatchers.Main) {
                    val player = model.player as ExoPlayer
                    val counters = player.videoDecoderCounters
                    counters?.ensureUpdated()
                    val dropped = counters?.droppedBufferCount ?: 0
                    if (sample > 1) largestDrop = maxOf(largestDrop, dropped - previousDropped)
                    previousDropped = dropped
                    Log.i("BreadProbe", "sample=$sample position=${player.currentPosition} buffer=${player.bufferedPosition} " +
                        "format=${player.videoFormat} rendered=${counters?.renderedOutputBufferCount} dropped=$dropped " +
                        "maxConsecutive=${counters?.maxConsecutiveDroppedBufferCount} speed=${player.playbackParameters.speed} " +
                        "processingUs=${counters?.totalVideoFrameProcessingOffsetUs} state=${player.playbackState}")
                    assertNull(model.uiState.value.playback.errorMessage)
                    assertEquals(1f, player.playbackParameters.speed, .01f)
                }
            }
            Log.i("BreadProbe", "largestDroppedBurst=$largestDrop textureFrames=$textureFrames textureLongGaps=$textureLongGaps textureMaxGapMs=$textureMaxGapMs")
            assertTrue("Sustained frame loss: $largestDrop in five seconds", largestDrop < 15)
        } finally {
            withContext(Dispatchers.Main) {
                texture?.surfaceTextureListener = originalListener
                model.closePlayback(); model.setPreferredVideoQuality(oldQuality); model.player.volume = oldVolume
            }
            scenario.close()
        }
    }
}
