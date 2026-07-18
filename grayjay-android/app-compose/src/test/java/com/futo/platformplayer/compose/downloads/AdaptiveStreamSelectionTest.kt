package com.futo.platformplayer.compose.downloads

import androidx.media3.common.StreamKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdaptiveStreamSelectionTest {
    @Test
    fun singleRenditionHlsAllowsEmptySelectionsAndStreamKeys() {
        assertEquals(
            emptyList<StreamKey>(),
            validatedAdaptiveStreamKeys(
                isHlsManifest = true,
                hasSelectedTracks = false,
                streamKeys = emptyList(),
            ),
        )
    }

    @Test
    fun selectedDashTrackAllowsEmptyStreamKeys() {
        assertEquals(
            emptyList<StreamKey>(),
            validatedAdaptiveStreamKeys(
                isHlsManifest = false,
                hasSelectedTracks = true,
                streamKeys = emptyList(),
            ),
        )
    }

    @Test
    fun missingTrackSelectionIsRejected() {
        val error = assertThrows(IllegalStateException::class.java) {
            validatedAdaptiveStreamKeys(
                isHlsManifest = false,
                hasSelectedTracks = false,
                streamKeys = emptyList(),
            )
        }

        assertEquals(
            "The adaptive manifest returned no selectable download streams.",
            error.message,
        )
    }
}
