package com.futo.platformplayer.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalUrlRoutingTest {
    @Test
    fun `known platform hosts prioritize their matching plugin`() {
        assertEquals("youtube", sourceIdHintForUrl("https://youtu.be/example"))
        assertEquals("youtube", sourceIdHintForUrl("https://music.youtube.com/watch?v=example"))
        assertEquals("odysee", sourceIdHintForUrl("https://odysee.com/@creator/video"))
        assertEquals("dailymotion", sourceIdHintForUrl("https://www.dailymotion.com/video/example"))
        assertEquals("bilibili", sourceIdHintForUrl("https://b23.tv/example"))
    }

    @Test
    fun `unknown hosts are left for custom plugin probing`() {
        assertNull(sourceIdHintForUrl("https://video.example.test/watch/123"))
    }
}
