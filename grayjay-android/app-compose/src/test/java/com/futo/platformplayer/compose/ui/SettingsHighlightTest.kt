package com.futo.platformplayer.compose.ui

import androidx.compose.ui.graphics.Color
import com.futo.platformplayer.compose.ui.screens.highlightSettingsMatches
import com.futo.platformplayer.compose.ui.screens.musicTerminologyResource
import com.futo.platformplayer.compose.R
import org.junit.Assert.*
import org.junit.Test

class SettingsHighlightTest {
    @Test fun matchesKeepOriginalTextAndAccentedCharacterOffsets() {
        val text = "Qualità audio e qualità video"
        val result = highlightSettingsMatches(text, "qualita", Color.Yellow, Color.Black)
        assertEquals(text, result.text)
        assertEquals(listOf(0, 16), result.spanStyles.map { it.start })
        assertEquals(listOf(7, 23), result.spanStyles.map { it.end })
    }
    @Test fun spotifySearchUsesTracksAndArtists() {
        assertEquals(R.string.music_tracks, musicTerminologyResource(R.string.videos, true))
        assertEquals(R.string.music_artists, musicTerminologyResource(R.string.creators, true))
        assertEquals(R.string.music_artists, musicTerminologyResource(R.string.channels_label, true))
        assertEquals(R.string.videos, musicTerminologyResource(R.string.videos, false))
    }
}
