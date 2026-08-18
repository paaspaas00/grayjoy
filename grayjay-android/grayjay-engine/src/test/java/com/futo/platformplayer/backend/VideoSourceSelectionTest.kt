package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSourceSelectionTest {
    @Test
    fun `muxed original audio wins over higher bitrate auto dub`() {
        val sources = listOf(
            source("en", original = false, priority = true, height = 1080, bitrate = 8_000_000),
            source("it", original = true, priority = false, height = 1080, bitrate = 6_000_000),
        )

        assertEquals(1, selectPreferredVideoSourceIndex(sources, "en", preferOriginal = true))
    }

    @Test
    fun `configured language wins when original preference is disabled`() {
        val sources = listOf(
            source("it", original = true, height = 1080),
            source("en-US", original = false, height = 720),
        )

        assertEquals(1, selectPreferredVideoSourceIndex(sources, "en", preferOriginal = false))
    }

    private fun source(
        language: String,
        original: Boolean,
        priority: Boolean = false,
        height: Int,
        bitrate: Int = 1_000_000,
    ) = VideoSourcePreference(
        language = language,
        isOriginal = original,
        isPriority = priority,
        height = height,
        bitrate = bitrate,
    )
}
