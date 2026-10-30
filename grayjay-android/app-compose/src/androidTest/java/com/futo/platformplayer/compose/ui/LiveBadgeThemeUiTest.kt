package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.data.YOUTUBE_SOURCE_ACCENT
import com.futo.platformplayer.compose.ui.screens.CompactVideoCard
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LiveBadgeThemeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun liveBadgeUsesTheMatchingForegroundInDarkAndLightThemes() {
        var dark by mutableStateOf(true)
        var accent by mutableStateOf<Long?>(YOUTUBE_SOURCE_ACCENT)
        var expectedForeground = Color.Unspecified
        var background = Color.Unspecified
        rule.setContent { GrayjayTheme(darkTheme = dark, dynamicColor = false, sourceAccentColor = accent) {
            expectedForeground = MaterialTheme.colorScheme.onError
            background = MaterialTheme.colorScheme.error
            Surface { Column {
                CompactVideoCard(VideoUiModel("live", "A live stream", "Creator", "", "In diretta", isLive = true), 0,
                    onClick = {}, animateEntrance = false)
                CompactVideoCard(VideoUiModel("regular", "A regular video", "Creator", "", "12:34"), 1,
                    onClick = {}, animateEntrance = false)
            } }
        } }
        for ((night, color) in listOf(true to YOUTUBE_SOURCE_ACCENT, true to null, false to YOUTUBE_SOURCE_ACCENT)) {
            rule.runOnIdle { dark = night; accent = color }
            val layout = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("In diretta", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(expectedForeground, layout.single().layoutInput.style.color)
            val light = maxOf(background.luminance(), expectedForeground.luminance())
            val dim = minOf(background.luminance(), expectedForeground.luminance())
            assertTrue("Live badge must have readable contrast", (light + .05f) / (dim + .05f) >= 4.5f)
            val regular = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("12:34", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(regular) }
            assertEquals(Color.White, regular.single().layoutInput.style.color)
            if (night && color != null) {
                val i = InstrumentationRegistry.getInstrumentation()
                i.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(i.targetContext.getExternalFilesDir(null), "live-badge-dark-red.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
