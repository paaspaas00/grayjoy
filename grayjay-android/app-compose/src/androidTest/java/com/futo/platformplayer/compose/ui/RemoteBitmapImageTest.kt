package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.RemoteBitmapImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemoteBitmapImageTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun rendersLocalImageFallbackAndClearsPreviousImageWhenSourceChanges() {
        val resources = "android.resource://${InstrumentationRegistry.getInstrumentation().context.packageName}/drawable/"
        val url = mutableStateOf(resources + "audit_moon")
        val fallback = mutableStateOf<String?>(null)
        rule.setContent {
            RemoteBitmapImage(url.value, Color.Magenta, Modifier.size(80.dp).testTag("image"),
                fallbackUrl = fallback.value, requestSize = IntSize(80, 80))
        }
        fun pixel(): Int {
            val image = rule.onNodeWithTag("image").captureToImage()
            return image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
        }
        rule.waitUntil(10_000) { pixel() != Color.Magenta.toArgb() }
        val first = pixel()
        rule.runOnIdle {
            url.value = resources + "missing_image"
            fallback.value = resources + "audit_earth"
        }
        rule.waitUntil(10_000) { val color = pixel(); color != Color.Magenta.toArgb() && color != first }
        rule.runOnIdle { url.value = ""; fallback.value = null }
        rule.waitForIdle()
        assertEquals(Color.Magenta.toArgb(), pixel())
    }
}
