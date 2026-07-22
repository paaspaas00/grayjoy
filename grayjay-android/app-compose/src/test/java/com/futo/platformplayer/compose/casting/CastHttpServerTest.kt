package com.futo.platformplayer.compose.casting

import java.net.InetAddress
import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastHttpServerTest {
    @Test
    fun `dash manifest is exposed over lan and upstream urls are proxied`() {
        val server = CastHttpServer()
        try {
            val castUrl = server.serveDash(
                manifest = """
                    <?xml version="1.0"?>
                    <MPD><Period><BaseURL>https://media.example/video.mp4?x=1&amp;y=2</BaseURL></Period></MPD>
                """.trimIndent(),
                localAddress = InetAddress.getLoopbackAddress(),
                dataSourceFactory = null,
                requestHeaders = emptyMap(),
            )

            val servedManifest = URL(castUrl).readText()
            assertTrue(servedManifest.contains("/stream-"))
            assertFalse(servedManifest.contains("media.example"))
        } finally {
            server.stop()
        }
    }
}
