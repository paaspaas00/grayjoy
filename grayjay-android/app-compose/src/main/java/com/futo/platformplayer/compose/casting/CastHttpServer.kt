package com.futo.platformplayer.compose.casting

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import com.futo.platformplayer.compose.net.BoundedSocketServer
import com.futo.platformplayer.compose.net.readHttpRequest

/**
 * Chromecast cannot consume the in-memory DASH manifests returned by Grayjay plugins and it
 * cannot execute plugin request modifiers. This small LAN-only bridge mirrors legacy Grayjay:
 * manifests are served from the phone and every referenced stream is fetched through the same
 * Media3 data source used by local playback.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CastHttpServer {
    private sealed interface Route

    private data class ConstantRoute(
        val contentType: String,
        val bytes: ByteArray,
    ) : Route

    private data class ProxyRoute(
        val upstreamUrl: String,
        val contentType: String?,
        val dataSourceFactory: HttpDataSource.Factory?,
        val requestHeaders: Map<String, String>,
    ) : Route

    private data class HlsRoute(
        val upstreamUrl: String,
        val dataSourceFactory: HttpDataSource.Factory?,
        val requestHeaders: Map<String, String>,
        val localAddress: InetAddress,
    ) : Route

    private val routes = CastRouteRegistry<Route>()
    private val server = BoundedSocketServer("Grayjoy-CastHttp", workerCount = 8, pendingCount = 16, handle = ::handle)

    val port: Int get() = server.port

    @Synchronized
    fun start() = server.start()

    fun clearRoutes() { routes.reset() }

    @Synchronized
    fun stop() {
        server.stop()
        routes.reset()
    }

    @Synchronized
    fun serveDash(
        manifest: String,
        localAddress: InetAddress,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        start()
        val generation = routes.reset()
        var rewritten = manifest
        DASH_URL.findAll(manifest).map { it.value }.distinct().forEach { encodedUrl ->
            val upstream = encodedUrl.xmlUnescape()
            val proxyPath = addProxyRoute(
                upstreamUrl = upstream,
                contentType = null,
                dataSourceFactory = dataSourceFactory,
                requestHeaders = requestHeaders,
                generation = generation,
            )
            rewritten = rewritten.replace(encodedUrl, localUrl(localAddress, proxyPath))
        }
        val manifestPath = routes.add(ConstantRoute(
            contentType = "application/dash+xml",
            bytes = rewritten.toByteArray(StandardCharsets.UTF_8),
        ), generation, "dash", ".mpd")
        return localUrl(localAddress, manifestPath)
    }

    @Synchronized
    fun serveHls(
        upstreamUrl: String,
        localAddress: InetAddress,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        start()
        val generation = routes.reset()
        val path = routes.add(HlsRoute(upstreamUrl, dataSourceFactory, requestHeaders, localAddress), generation, "hls", ".m3u8")
        return localUrl(localAddress, path)
    }

    @Synchronized
    fun serveProgressive(
        upstreamUrl: String,
        contentType: String?,
        localAddress: InetAddress,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        start()
        val generation = routes.reset()
        return localUrl(
            localAddress,
            addProxyRoute(upstreamUrl, contentType, dataSourceFactory, requestHeaders, generation),
        )
    }

    private fun addProxyRoute(
        upstreamUrl: String,
        contentType: String?,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
        generation: Long,
    ): String {
        return routes.add(ProxyRoute(upstreamUrl, contentType, dataSourceFactory, requestHeaders), generation, "stream")
    }

    private fun handle(socket: java.net.Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val request = readHttpRequest(input, maxBodyBytes = 0) ?: return
        val method = request.method
        val path = request.target.substringBefore('?')
        val headers = request.headers
        if (method == "OPTIONS") {
            output.writeHeaders(204, "No Content", mapOf("Content-Length" to "0"))
            output.flush()
            return
        }
        if (method != "GET" && method != "HEAD") {
            output.writeTextResponse(405, "Method Not Allowed", "Method not allowed")
            return
        }
        val route = routes[path]
        if (route == null) {
            output.writeTextResponse(404, "Not Found", "Not found")
            return
        }
        when (val value = route.route) {
            is ConstantRoute -> {
                output.writeHeaders(
                    200,
                    "OK",
                    mapOf(
                        "Content-Type" to value.contentType,
                        "Content-Length" to value.bytes.size.toString(),
                    ),
                )
                if (method != "HEAD") output.write(value.bytes)
                output.flush()
            }
            is ProxyRoute -> proxy(value, method, headers, output)
            is HlsRoute -> serveHlsPlaylist(value, route.generation, method, output)
        }
    }

    private fun serveHlsPlaylist(route: HlsRoute, generation: Long, method: String, output: BufferedOutputStream) {
        val content = readAll(route.upstreamUrl, route.dataSourceFactory, route.requestHeaders)
            .toString(StandardCharsets.UTF_8)
        val rewritten = rewriteHlsPlaylist(content, route.upstreamUrl) { upstream, playlist ->
            val path = if (playlist) {
                routes.add(route.copy(upstreamUrl = upstream), generation, "hls", ".m3u8")
            } else {
                addProxyRoute(upstream, null, route.dataSourceFactory, route.requestHeaders, generation)
            }
            localUrl(route.localAddress, path)
        }.toByteArray(StandardCharsets.UTF_8)
        output.writeHeaders(
            200,
            "OK",
            mapOf(
                "Content-Type" to "application/vnd.apple.mpegurl",
                "Content-Length" to rewritten.size.toString(),
            ),
        )
        if (method != "HEAD") output.write(rewritten)
        output.flush()
    }

    private fun proxy(
        route: ProxyRoute,
        method: String,
        requestHeaders: Map<String, String>,
        output: BufferedOutputStream,
    ) {
        val rangeHeader = requestHeaders["range"]
        val range = rangeHeader?.let(::parseCastByteRange)
        if (rangeHeader != null && range == null) {
            output.writeTextResponse(416, "Range Not Satisfiable", "Invalid byte range")
            return
        }
        val source = (route.dataSourceFactory ?: defaultFactory(route.requestHeaders)).createDataSource()
        route.requestHeaders.forEach(source::setRequestProperty)
        range?.suffix?.let { source.setRequestProperty("Range", "bytes=-$it") }
        val spec = DataSpec.Builder()
            .setUri(route.upstreamUrl)
            .setPosition(range?.start ?: 0L)
            .apply {
                range?.length?.let(::setLength)
            }
            .build()
        try {
            val length = source.open(spec)
            val responseCode = source.responseCode
            val partial = (range != null && range.suffix == null) || responseCode == 206
            val responseHeaders = source.responseHeaders
            val contentType = route.contentType
                ?: responseHeaders.headerValue("Content-Type")
                ?: inferContentType(route.upstreamUrl)
            val headers = linkedMapOf(
                "Content-Type" to contentType,
                "Accept-Ranges" to "bytes",
            )
            if (length != C.LENGTH_UNSET.toLong()) headers["Content-Length"] = length.toString()
            if (partial) {
                val contentRange = castContentRange(range, length, responseHeaders.headerValue("Content-Range"))
                if (contentRange == null) {
                    output.writeTextResponse(502, "Bad Gateway", "Invalid upstream byte range")
                    return
                }
                headers["Content-Range"] = contentRange
            }
            output.writeHeaders(if (partial) 206 else 200, if (partial) "Partial Content" else "OK", headers)
            if (method != "HEAD") {
                val buffer = ByteArray(PROXY_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    if (read <= 0) throw java.io.IOException("Cast stream made no progress")
                    output.write(buffer, 0, read)
                }
            }
            output.flush()
        } finally {
            runCatching(source::close)
        }
    }

    private fun readAll(
        url: String,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): ByteArray {
        val source = (dataSourceFactory ?: defaultFactory(requestHeaders)).createDataSource()
        requestHeaders.forEach(source::setRequestProperty)
        return try {
            source.open(DataSpec.Builder().setUri(url).build())
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(PROXY_BUFFER_SIZE)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                if (read <= 0 || output.size().toLong() + read > MAX_MANIFEST_BYTES) {
                    throw java.io.IOException("Invalid or oversized cast manifest")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } finally {
            runCatching(source::close)
        }
    }

    private fun defaultFactory(headers: Map<String, String>): HttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

    private fun localUrl(address: InetAddress, path: String): String {
        val hostAddress = requireNotNull(address.hostAddress)
        val host = if (address is Inet6Address) "[${hostAddress.substringBefore('%')}]"
        else hostAddress
        return "http://$host:$port$path"
    }

    private fun BufferedOutputStream.writeTextResponse(code: Int, reason: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(
            code,
            reason,
            mapOf("Content-Type" to "text/plain; charset=utf-8", "Content-Length" to bytes.size.toString()),
        )
        write(bytes)
        flush()
    }

    private fun BufferedOutputStream.writeHeaders(
        code: Int,
        reason: String,
        headers: Map<String, String>,
    ) {
        val text = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Range, Content-Type\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Connection: close\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        write(text.toByteArray(StandardCharsets.US_ASCII))
    }


    private companion object {
        const val SOCKET_TIMEOUT_MS = 30_000
        const val PROXY_BUFFER_SIZE = 64 * 1024
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        val DASH_URL = Regex("https?://[^\\s<>\\\"]+")

        fun String.xmlUnescape(): String = replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

        fun Map<String, List<String>>.headerValue(name: String): String? = entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()

        fun inferContentType(url: String): String = when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mpd" -> "application/dash+xml"
            "vtt" -> "text/vtt"
            else -> "application/octet-stream"
        }
    }
}
