package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSourceSelectionTest {
    @Test
    fun `original audio wins over configured language when enabled`() {
        val sources = listOf(
            source("en", bitrate = 192_000),
            source("it", original = true, bitrate = 128_000),
        )

        assertEquals(1, selectPreferredAudioSourceIndex(sources, "en", preferOriginal = true))
    }

    @Test
    fun `configured language wins when original preference is disabled`() {
        val sources = listOf(
            source("en", original = true, bitrate = 192_000),
            source("it-IT", bitrate = 128_000),
        )

        assertEquals(1, selectPreferredAudioSourceIndex(sources, "it", preferOriginal = false))
    }

    @Test
    fun `english is the fallback before an unrelated language`() {
        val sources = listOf(
            source("de", bitrate = 256_000),
            source("en-US", bitrate = 128_000),
        )

        assertEquals(1, selectPreferredAudioSourceIndex(sources, "it", preferOriginal = false))
    }

    @Test
    fun `priority sources are selected before all other preferences`() {
        val sources = listOf(
            source("it", original = true, bitrate = 256_000),
            source("en", priority = true, bitrate = 96_000),
        )

        assertEquals(1, selectPreferredAudioSourceIndex(sources, "it", preferOriginal = true))
    }

    private fun source(
        language: String,
        original: Boolean = false,
        priority: Boolean = false,
        bitrate: Int,
    ) = AudioSourcePreference(
        language = language,
        isOriginal = original,
        isPriority = priority,
        bitrate = bitrate,
    )
}
