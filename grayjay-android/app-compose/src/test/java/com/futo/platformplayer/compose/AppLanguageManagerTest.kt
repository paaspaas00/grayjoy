package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.GrayjayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppLanguageManagerTest {
    @Test
    fun unsupportedOrBlankLanguageFallsBackToAndroidSystem() {
        assertEquals("", AppLanguageManager.normalizeLanguageTag(null))
        assertEquals("", AppLanguageManager.normalizeLanguageTag(""))
        assertEquals("", AppLanguageManager.normalizeLanguageTag("nl-NL"))
    }

    @Test
    fun regionalLanguageTagsResolveToBundledTranslation() {
        assertEquals("it", AppLanguageManager.normalizeLanguageTag("it-IT"))
        assertEquals("pt", AppLanguageManager.normalizeLanguageTag("pt-BR"))
        assertEquals("zh", AppLanguageManager.normalizeLanguageTag("zh-Hant-TW"))
    }

    @Test
    fun holdToSpeedIsOptIn() {
        assertFalse(GrayjayUiState().holdToSpeedEnabled)
    }
}
