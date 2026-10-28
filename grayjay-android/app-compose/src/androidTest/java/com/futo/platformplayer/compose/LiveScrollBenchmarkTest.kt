package com.futo.platformplayer.compose

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Collections

/** Optional real-library scrolling, with touch events paced like a finger rather than ADB's event flood. */
class LiveScrollBenchmarkTest {
    @Test fun homeScrollWithRealPagingAndArtwork() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveScroll") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frames = Collections.synchronizedList(mutableListOf<Double>())
        val thread = HandlerThread("live-scroll-frames").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ -> frames.add(frame.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0) }
        // An explicit activity intent leaves the startup wizard preference untouched and
        // measures the content window rather than the wizard's separate dialog window.
        ActivityScenario.launch<MainActivity>(android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            SystemClock.sleep(8_000)
            scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
            var width = 0
            var height = 0
            scenario.onActivity { width = it.window.decorView.width; height = it.window.decorView.height }
            check(width > 0 && height > 0)
            val x = width * .5f
            val top = height * .27f
            val bottom = height * .77f
            fun capture(name: String) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            capture("live-scroll-before.png")
            fun swipe(from: Float, to: Float) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, y: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.sendPointerSync(event); event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, from)
                repeat(30) { i -> SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE, from + (to - from) * (i + 1) / 30f) }
                send(MotionEvent.ACTION_UP, to)
                SystemClock.sleep(250)
            }
            try {
                swipe(bottom, top); swipe(top, bottom)
                frames.clear()
                repeat(6) { swipe(bottom, top) }
                repeat(3) { swipe(top, bottom) }
            } finally {
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                thread.quitSafely()
            }
            val samples = synchronized(frames) { frames.sorted() }
            capture("live-scroll-after.png")
            check(samples.size > 10) { "Only ${samples.size} frames in ${width}x$height viewport" }
            fun percentile(p: Double) = samples[((samples.size - 1) * p).toInt()]
            File(instrumentation.targetContext.getExternalFilesDir(null), "honor-live-scroll.json").writeText(
                JSONObject().put("frames", samples.size).put("p50_ms", percentile(.5)).put("p90_ms", percentile(.9))
                    .put("over_32ms", samples.count { it > 32 }).put("over_64ms", samples.count { it > 64 }).toString(2))
        }
    }
}
