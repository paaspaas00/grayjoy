package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.data.YOUTUBE_SOURCE_ACCENT
import com.futo.platformplayer.compose.ui.screens.SeekVideoBlur
import com.futo.platformplayer.compose.ui.screens.playerViewLayout
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

class SeekVideoBlurUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun textureVideoBlursAndRestoresWhileControlsStaySharp() = verifyBlur(false, false)
    @Test fun fullscreenSurfaceBlursWithoutReplacingItsVideoOutput() = verifyBlur(true, false)
    @Test fun protectedVideoNeverCreatesACopyOverlay() = verifyBlur(true, true)

    private fun verifyBlur(fullscreen: Boolean, protected: Boolean) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        var seeking by mutableStateOf(false)
        var playerView by mutableStateOf<PlayerView?>(null)
        val drawn = AtomicBoolean()
        rule.setContent { GrayjayTheme(darkTheme = true, dynamicColor = false, sourceAccentColor = YOUTUBE_SOURCE_ACCENT) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("YouTube", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(true, {}, { Text("Video") })
                        FilterChip(false, {}, { Text("Commenti") })
                        Button({}) { Text("Segui già") }
                        Switch(checked = true, onCheckedChange = {})
                    }
                    Box(Modifier.fillMaxWidth().height(240.dp).background(Color.Black).testTag("blur-fixture")) {
                        AndroidView(factory = { context ->
                            (LayoutInflater.from(context).inflate(playerViewLayout(false, fullscreen), null, false) as PlayerView).apply {
                                useController = false
                                findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter).visibility = android.view.View.GONE
                                when (val surface = videoSurfaceView) {
                                    is SurfaceView -> surface.holder.addCallback(object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) = draw(holder)
                                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = draw(holder)
                                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                                        private fun draw(holder: SurfaceHolder) {
                                            val canvas = holder.lockCanvas() ?: return
                                            drawPattern(canvas); holder.unlockCanvasAndPost(canvas); drawn.set(true)
                                        }
                                    })
                                    is TextureView -> surface.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = draw()
                                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = draw()
                                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                                        private fun draw() {
                                            val canvas = surface.lockCanvas() ?: return
                                            drawPattern(canvas); surface.unlockCanvasAndPost(canvas); drawn.set(true)
                                        }
                                    }
                                }
                            }
                        }, update = { playerView = it }, modifier = Modifier.fillMaxSize())
                        SeekVideoBlur(playerView, seeking, protected)
                        Text("CONTROLLI", color = Color.Black,
                            modifier = Modifier.align(Alignment.TopCenter).background(Color.Yellow).padding(12.dp).testTag("sharp-control"))
                    }
                    Text("Anteprima e comandi nitidi", color = MaterialTheme.colorScheme.primary)
                }
            }
        } }
        rule.waitUntil(5_000) { drawn.get() }
        rule.waitForIdle()
        android.os.SystemClock.sleep(160)
        val originalView = playerView!!.videoSurfaceView
        val bounds = rule.onNodeWithTag("blur-fixture").fetchSemanticsNode().boundsInRoot
        val control = rule.onNodeWithTag("sharp-control").fetchSemanticsNode().boundsInRoot
        val before = screenshot()
        save(before, "oct26-${if (fullscreen) "surface" else "texture"}-before")
        val baseline = edgeEnergy(before, bounds)
        assertTrue("Fixture must contain visible sharp edges: $baseline at $bounds", baseline > .1)
        rule.runOnIdle { seeking = true }
        rule.mainClock.advanceTimeBy(240)
        if (fullscreen && !protected) rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("player-seek-blur-frame").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        android.os.SystemClock.sleep(160)
        val blurred = screenshot()
        save(blurred, "oct26-${if (fullscreen) "surface" else "texture"}-${if (protected) "protected" else "blurred"}")
        assertSame("Seek must not detach or replace the video surface", originalView, playerView!!.videoSurfaceView)
        if (protected) {
            rule.onNodeWithTag("player-seek-blur-frame").assertDoesNotExist()
            assertTrue(edgeEnergy(blurred, bounds) > baseline * .7)
        } else assertTrue("The video should be visibly softened", edgeEnergy(blurred, bounds) < baseline * .55)
        assertEquals("Controls must not be blurred", edgeEnergy(before, control), edgeEnergy(blurred, control), .02)
        val outsideX = (bounds.left - 3f).roundToInt().coerceAtLeast(0)
        val outsideY = bounds.center.y.roundToInt()
        assertEquals("Blur must not leak outside the video rectangle", before.getPixel(outsideX, outsideY), blurred.getPixel(outsideX, outsideY))
        rule.runOnIdle { seeking = false }
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithTag("player-seek-blur").assertDoesNotExist()
        rule.waitForIdle()
        android.os.SystemClock.sleep(100)
        val after = screenshot()
        assertTrue("Video must become sharp again", edgeEnergy(after, bounds) > baseline * .85)
        save(after, "oct26-${if (fullscreen) "surface" else "texture"}-restored")
        before.recycle(); blurred.recycle(); after.recycle()
    }

    private fun drawPattern(canvas: Canvas) {
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint().apply { color = android.graphics.Color.WHITE }
        for (x in 0 until canvas.width step 8) canvas.drawRect(x.toFloat(), 0f, (x + 4).toFloat(), canvas.height.toFloat(), paint)
    }
    private fun screenshot(): Bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    private fun edgeEnergy(bitmap: Bitmap, bounds: Rect): Double {
        val left = (bounds.left + bounds.width * .2f).roundToInt().coerceAtLeast(0)
        val right = (bounds.right - bounds.width * .2f).roundToInt().coerceAtMost(bitmap.width - 1)
        val top = (bounds.top + bounds.height * .4f).roundToInt().coerceAtLeast(0)
        val bottom = (bounds.top + bounds.height * .7f).roundToInt().coerceAtMost(bitmap.height - 1)
        var difference = 0.0; var count = 0
        for (y in top until bottom step 3) for (x in left until right) {
            difference += abs((bitmap.getPixel(x, y) and 255) - (bitmap.getPixel(x + 1, y) and 255)) / 255.0
            count++
        }
        return difference / count.coerceAtLeast(1)
    }
    private fun save(bitmap: Bitmap, name: String) {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
