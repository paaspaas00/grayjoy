package com.futo.platformplayer.compose.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.futo.platformplayer.compose.data.YOUTUBE_SOURCE_ACCENT
import org.junit.Assert.*
import org.junit.Test

class SourceProfileColorTest {
    @Test fun youtubeDarkUsesMutedRedWithReadableTextAndContainers() {
        val colors = sourceProfileColorScheme(darkColorScheme(), YOUTUBE_SOURCE_ACCENT, true)
        assertEquals(Color(0xFFD9625C), colors.primary)
        assertTrue(colors.primary.luminance() < .25f)
        assertTrue(contrast(colors.primary, colors.onPrimary) >= 4.5f)
        assertTrue(contrast(colors.primary, colors.surfaceContainer) >= 4.5f)
        assertTrue(contrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5f)
        assertTrue(contrast(colors.primaryContainer, colors.background) >= 1.5f)
    }
    @Test fun youtubeLightKeepsTheChosenDeepRed() {
        val colors = sourceProfileColorScheme(lightColorScheme(), YOUTUBE_SOURCE_ACCENT, false)
        assertEquals(Color(YOUTUBE_SOURCE_ACCENT), colors.primary)
        assertTrue(contrast(colors.primary, colors.onPrimary) >= 4.5f)
    }
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
