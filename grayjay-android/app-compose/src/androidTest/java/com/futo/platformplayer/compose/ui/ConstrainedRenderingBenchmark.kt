package com.futo.platformplayer.compose.ui

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.DevicePerformanceProfile
import com.futo.platformplayer.compose.LocalDevicePerformanceProfile
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections

/** Real-time input and Android frame timings; isolated fixtures, not the user's library. */
class ConstrainedRenderingBenchmark {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun compareSameScreensWithLegacyAndConstrainedRendering() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRenderingBenchmark") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        com.futo.platformplayer.compose.ForegroundWorkBudget.enter(true)
        val source = instrumentation.context.packageName
        val videos = List(240) { i -> VideoUiModel("bench-$i", "Una prospettiva diversa: scienza e tecnologia $i",
            "Laboratorio delle idee", "12.345 visualizzazioni · 2 giorni fa", "12:34",
            thumbnailUrl = "android.resource://$source/drawable/audit_earth",
            authorThumbnailUrl = "android.resource://$source/drawable/audit_moon") }
        val state = GrayjayUiState(videos = videos, libraryVideos = videos, home = HomeUiState(videos = videos))
        val constrained = mutableStateOf(false)
        lateinit var player: ExoPlayer
        rule.runOnUiThread { rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity).build() }
        rule.setContent { CompositionLocalProvider(LocalDevicePerformanceProfile provides
            DevicePerformanceProfile(constrained.value, constrained.value, !constrained.value)) {
            GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, GrayjayAppActions()) }
        } }
        val timings = Collections.synchronizedList(mutableListOf<Double>())
        val capture = java.util.concurrent.atomic.AtomicBoolean(false)
        val thread = HandlerThread("render-audit").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            if (capture.get()) timings.add(frame.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0)
        }
        rule.runOnUiThread { rule.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        val report = JSONArray()
        try {
            // Alternate order to reduce cache/temperature bias; each measurement has an unrecorded warm-up.
            for (mode in listOf(false, true, true, false)) {
                rule.runOnIdle { constrained.value = mode }
                for (destination in listOf("home", "settings")) {
                    rule.onNodeWithTag("nav-$destination").performClick()
                    rule.waitForIdle()
                    val display = rule.activity.resources.displayMetrics
                    val x = display.widthPixels * .5f
                    val top = display.heightPixels * .3f
                    val bottom = display.heightPixels * .78f
                    swipe(x, bottom, top); swipe(x, top, bottom)
                    SystemClock.sleep(400)
                    timings.clear(); capture.set(true)
                    repeat(2) { swipe(x, bottom, top) }
                    repeat(2) { swipe(x, top, bottom) }
                    SystemClock.sleep(200); capture.set(false)
                    val samples = synchronized(timings) { timings.sorted() }
                    assertTrue("No frames captured for $destination", samples.size > 10)
                    fun percentile(p: Double) = samples[((samples.size - 1) * p).toInt()]
                    report.put(JSONObject().put("constrained", mode).put("screen", destination)
                        .put("frames", samples.size).put("p50_ms", percentile(.5)).put("p90_ms", percentile(.9))
                        .put("over_32ms", samples.count { it > 32.0 }).put("over_64ms", samples.count { it > 64.0 }))
                }
            }
        } finally {
            capture.set(false)
            rule.runOnUiThread { rule.activity.window.removeOnFrameMetricsAvailableListener(listener); player.release() }
            thread.quitSafely()
            com.futo.platformplayer.compose.ForegroundWorkBudget.leave()
            File(instrumentation.targetContext.getExternalFilesDir(null), "honor-render-benchmark.json").writeText(report.toString(2))
        }
    }
    private fun swipe(x: Float, from: Float, to: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            com.futo.platformplayer.compose.ForegroundWorkBudget.touch()
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event); event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, from)
        repeat(30) { step -> SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE, from + (to - from) * (step + 1) / 30f) }
        send(MotionEvent.ACTION_UP, to); SystemClock.sleep(180)
    }
}
