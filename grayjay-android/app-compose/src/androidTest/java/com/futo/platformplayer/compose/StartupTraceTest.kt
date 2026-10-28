package com.futo.platformplayer.compose

import android.os.Debug
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in sampling of the actual application's first launch; does not modify its library. */
class StartupTraceTest {
    @Test fun sampleStartupUntilFirstDraw() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("traceStartup") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "startup-sampling.trace")
        val started = SystemClock.elapsedRealtime()
        val draw = CountDownLatch(1)
        Debug.startMethodTracingSampling(file.absolutePath, 16 * 1024 * 1024, 2_000)
        var activity: ActivityScenario<MainActivity>? = null
        try {
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity.onActivity { screen ->
                val view = screen.window.decorView
                val listener = object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() { draw.countDown(); view.post { view.viewTreeObserver.removeOnDrawListener(this) } }
                }
                view.viewTreeObserver.addOnDrawListener(listener)
                view.invalidate()
            }
            check(draw.await(30, TimeUnit.SECONDS)) { "No application frame" }
        } finally {
            Debug.stopMethodTracing()
            File(context.getExternalFilesDir(null), "startup-sampling-time.txt").writeText("${SystemClock.elapsedRealtime() - started} ms (sampling enabled)")
            activity?.close()
        }
    }
}
