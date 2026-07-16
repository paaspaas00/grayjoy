package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginContentUrlTest {
    @Test
    fun `uses the canonical content url when one is available`() {
        val video = video(
            id = "synthetic-id",
            contentUrl = "https://www.youtube.com/watch?v=abc123",
        )

        assertEquals("https://www.youtube.com/watch?v=abc123", video.pluginContentUrlOrNull())
    }

    @Test
    fun `uses a url-shaped id only as a compatibility fallback`() {
        val video = video(id = "https://youtu.be/abc123")

        assertEquals("https://youtu.be/abc123", video.pluginContentUrlOrNull())
    }

    @Test
    fun `does not send synthetic local ids to a source plugin`() {
        assertNull(video(id = "privacy-first-library").pluginContentUrlOrNull())
    }

    private fun video(id: String, contentUrl: String = "") = VideoUiModel(
        id = id,
        title = "Video",
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        contentUrl = contentUrl,
    )
}
