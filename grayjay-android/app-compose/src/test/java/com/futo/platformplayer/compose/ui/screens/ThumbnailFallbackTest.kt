package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailFallbackTest {
    @Test
    fun youtubePluginThumbnailGetsStableFallback() {
        assertEquals(
            "https://i.ytimg.com/vi/N3EOh2gKZoc/hqdefault.jpg",
            youtubeThumbnailFallbackUrl(
                sourceId = "youtube",
                videoId = "https://www.youtube.com/watch?v=N3EOh2gKZoc",
                thumbnailUrl = "https://i.ytimg.com/vi/N3EOh2gKZoc/hqdefault_custom_2.jpg?expired=1",
            ),
        )
    }

    @Test
    fun otherSourcesAreNotRewritten() {
        assertNull(
            youtubeThumbnailFallbackUrl(
                sourceId = "peertube",
                videoId = "video-1",
                thumbnailUrl = "https://example.test/thumb.jpg",
            ),
        )
    }
}
