package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.onboarding.ShortsGestureDemo
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShortsGestureDemoTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun handDemonstratesBothDirectionsAndProducesVisualReviewFrames() {
        rule.mainClock.autoAdvance = false
        rule.setContent { GrayjayTheme(darkTheme = true, dynamicColor = false) { Surface { ShortsGestureDemo(active = true) } } }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "wizard-demo-review").apply { mkdirs() }
        var previousTime = 0L
        val frames = mutableListOf<Bitmap>()
        for (time in listOf(600L, 1500L, 2400L, 3600L, 4500L, 5400L)) {
            rule.mainClock.advanceTimeBy(time - previousTime)
            previousTime = time
            val bitmap = rule.onNodeWithTag("shorts-gesture-demo").captureToImage().asAndroidBitmap()
            frames += bitmap
            File(directory, "gesture-$time.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
        }
        assertFalse("Touch and completed swipe must not look the same", frames[0].sameAs(frames[2]))
        assertFalse("The next and previous cards must differ", frames[2].sameAs(frames[5]))
    }

    @Test fun inactiveDemoStaysStillButKeepsAVisibleGestureCue() {
        rule.mainClock.autoAdvance = false
        rule.setContent { GrayjayTheme(darkTheme = false, dynamicColor = false) { Surface { ShortsGestureDemo(active = false) } } }
        val before = rule.onNodeWithTag("shorts-gesture-demo").captureToImage().asAndroidBitmap()
        rule.mainClock.advanceTimeBy(6_000)
        val after = rule.onNodeWithTag("shorts-gesture-demo").captureToImage().asAndroidBitmap()
        assertTrue(before.sameAs(after))
    }
}
