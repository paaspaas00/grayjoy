package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.AudioQualityUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualitySelectionTest {
    private val variants = listOf(64_000, 128_000, 192_000, 256_000).map { bitrate ->
        AudioQualityUiModel(
            bitrate = bitrate,
            name = "$bitrate",
            playbackUrl = "https://media.example/$bitrate",
        )
    }

    @Test
    fun highAndAutomaticChooseHighestAvailableBitrate() {
        assertEquals(256_000, selectAudioQualityVariant(variants, Int.MAX_VALUE)?.bitrate)
        assertEquals(256_000, selectAudioQualityVariant(variants, null)?.bitrate)
    }

    @Test
    fun targetChoosesHighestBitrateNotExceedingTarget() {
        assertEquals(128_000, selectAudioQualityVariant(variants, 160_000)?.bitrate)
        assertEquals(192_000, selectAudioQualityVariant(variants, 192_000)?.bitrate)
    }

    @Test
    fun lowTargetFallsBackToLowestAvailableBitrate() {
        assertEquals(64_000, selectAudioQualityVariant(variants, 1)?.bitrate)
    }

    @Test
    fun noUsableVariantsReturnsNull() {
        assertNull(selectAudioQualityVariant(emptyList(), 128_000))
        assertNull(
            selectAudioQualityVariant(
                listOf(AudioQualityUiModel(0, "adaptive", "https://media.example/master")),
                128_000,
            ),
        )
    }
}
