package com.futo.platformplayer.compose.ui

import com.futo.platformplayer.compose.ui.screens.settingsTextMatches
import org.junit.Assert.*
import org.junit.Test

class SettingsQueryTest {
    @Test fun queryIgnoresCaseAccentsAndWordOrder() {
        assertTrue(settingsTextMatches("Qualità video preferita", "VIDEO qualita"))
        assertTrue(settingsTextMatches("Velocità di riproduzione", "velocita"))
        assertTrue(settingsTextMatches("Tieni premuto per la velocità 2×", "2X"))
        assertTrue(settingsTextMatches("Keep screen awake", " screen   keep "))
        assertFalse(settingsTextMatches("Audio language", "video language"))
        assertTrue(settingsTextMatches("Anything", ""))
    }
}
