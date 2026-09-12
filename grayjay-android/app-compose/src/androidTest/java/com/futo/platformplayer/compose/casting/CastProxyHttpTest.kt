package com.futo.platformplayer.compose.casting

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CastProxyHttpTest {
    private val resources = mapOf(
        "https://fixture/master" to "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvariant\n",
        "https://fixture/variant" to "#EXTM3U\n#EXTINF:6,\nsegment\n",
        "https://fixture/segment" to "0123456789",
    )

    @Test fun hlsReloadAndExtensionlessChildUseStableProxyRoutes() {
        val server = CastHttpServer()
        try {
            val root = server.serveHls("https://fixture/master", InetAddress.getLoopbackAddress(), factory(), emptyMap())
            val first = fetch(root).substringAfter("\r\n\r\n")
            repeat(20) { assertEquals(first, fetch(root).substringAfter("\r\n\r\n")) }
            val child = first.lineSequence().first { it.startsWith("http") }
            val playlist = fetch(child).substringAfter("\r\n\r\n")
            assertTrue(playlist.contains("/stream-"))
            val media = playlist.lineSequence().first { it.startsWith("http") }
            assertEquals("0123456789", fetch(media).substringAfter("\r\n\r\n"))
            assertEquals("234", fetch(media, "bytes=2-4").substringAfter("\r\n\r\n"))
            assertEquals("789", fetch(media, "bytes=-3").substringAfter("\r\n\r\n"))
            assertTrue(fetch(media, "bytes=8-2").startsWith("HTTP/1.1 416"))
            assertEquals("", fetch(media, method = "HEAD").substringAfter("\r\n\r\n"))
        } finally { server.stop() }
    }

    private fun factory() = object : HttpDataSource.Factory {
        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>) = this
        override fun createDataSource(): HttpDataSource {
            var bytes = ByteArray(0)
            var offset = 0
            var uri = android.net.Uri.EMPTY
            var contentRange: String? = null
            val headers = mutableMapOf<String, String>()
            return Proxy.newProxyInstance(
                HttpDataSource::class.java.classLoader, arrayOf(HttpDataSource::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "open" -> {
                        val spec = args[0] as DataSpec
                        uri = spec.uri
                        val original = resources.getValue(uri.toString()).toByteArray()
                        val suffix = headers["Range"]?.let(::parseCastByteRange)?.suffix
                        val start = if (suffix != null) (original.size - suffix.toInt()).coerceAtLeast(0) else spec.position.toInt()
                        val end = if (spec.length < 0) original.size else (start + spec.length.toInt()).coerceAtMost(original.size)
                        bytes = original.copyOfRange(start, end)
                        offset = 0
                        contentRange = if (start > 0 || end < original.size) "bytes $start-${end - 1}/${original.size}" else null
                        bytes.size.toLong()
                    }
                    "read" -> if (offset == bytes.size) -1 else {
                        val count = minOf(args[2] as Int, bytes.size - offset)
                        bytes.copyInto(args[0] as ByteArray, args[1] as Int, offset, offset + count)
                        offset += count
                        count
                    }
                    "getUri" -> uri
                    "getResponseCode" -> if (contentRange != null) 206 else 200
                    "getResponseHeaders" -> contentRange?.let { mapOf("Content-Range" to listOf(it)) } ?: emptyMap<String, List<String>>()
                    "setRequestProperty" -> { headers[args[0] as String] = args[1] as String; null }
                    "clearRequestProperty" -> { headers.remove(args[0] as String); null }
                    "clearAllRequestProperties" -> { headers.clear(); null }
                    else -> null
                }
            } as HttpDataSource
        }
    }

    private fun fetch(url: String, range: String? = null, method: String = "GET"): String {
        val uri = URI(url)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 5_000
            val request = "$method ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n" +
                (range?.let { "Range: $it\r\n" } ?: "") + "\r\n"
            socket.getOutputStream().write(request.toByteArray())
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }
}
