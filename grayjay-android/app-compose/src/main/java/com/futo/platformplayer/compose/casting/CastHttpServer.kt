package com.futo.platformplayer.compose.casting

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Chromecast cannot consume the in-memory DASH manifests returned by Grayjay plugins and it
 * cannot execute plugin request modifiers. This small LAN-only bridge mirrors legacy Grayjay:
 * manifests are served from the phone and every referenced stream is fetched through the same
 * Media3 data source used by local playback.
 */
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

    private val routes = ConcurrentHashMap<String, Route>()
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "Grayjoy-CastHttp").apply { isDaemon = true }
    }
    private var serverSocket: ServerSocket? = null

    val port: Int get() = serverSocket?.localPort ?: 0

    @Synchronized
    fun start() {
        if (running.get()) return
        val socket = ServerSocket(0)
        serverSocket = socket
        running.set(true)
        executor.execute {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                executor.execute { runCatching { handle(client) }.also { runCatching(client::close) } }
            }
        }
    }

    fun clearRoutes() = routes.clear()

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        routes.clear()
        executor.shutdownNow()
    }

    fun serveDash(
        manifest: String,
        localAddress: InetAddress,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        start()
        clearRoutes()
        var rewritten = manifest
        DASH_URL.findAll(manifest).map { it.value }.distinct().forEach { encodedUrl ->
            val upstream = encodedUrl.xmlUnescape()
            val proxyPath = addProxyRoute(
                upstreamUrl = upstream,
                contentType = null,
                dataSourceFactory = dataSourceFactory,
                requestHeaders = requestHeaders,
            )
            rewritten = rewritten.replace(encodedUrl, localUrl(localAddress, proxyPath))
        }
        val manifestPath = "/dash-${UUID.randomUUID()}.mpd"
        routes[manifestPath] = ConstantRoute(
            contentType = "application/dash+xml",
            bytes = rewritten.toByteArray(StandardCharsets.UTF_8),
        )
        return localUrl(localAddress, manifestPath)
    }

    fun serveHls(
        upstreamUrl: String,
        localAddress: InetAddress,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        start()
        clearRoutes()
        val path = "/hls-${UUID.randomUUID()}.m3u8"
        routes[path] = HlsRoute(upstreamUrl, dataSourceFactory, requestHeaders, localAddress)
        return localUrl(localAddress, path)
    }

    fun serveProgressive(
        upstreamUrl: String,
        contentType: String?,
        localAddress: InetAddress,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        start()
        clearRoutes()
        return localUrl(
            localAddress,
            addProxyRoute(upstreamUrl, contentType, dataSourceFactory, requestHeaders),
        )
    }

    private fun addProxyRoute(
        upstreamUrl: String,
        contentType: String?,
        dataSourceFactory: HttpDataSource.Factory?,
        requestHeaders: Map<String, String>,
    ): String {
        val path = "/stream-${UUID.randomUUID()}"
        routes[path] = ProxyRoute(upstreamUrl, contentType, dataSourceFactory, requestHeaders)
        return path
    }

    private fun handle(socket: java.net.Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val requestLine = input.readHttpLine() ?: return
        val requestParts = requestLine.split(' ')
        if (requestParts.size < 2) return
        val method = requestParts[0].uppercase()
        val requestTarget = requestParts[1]
        val path = requestTarget.substringBefore('?')
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = input.readHttpLine() ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        if (method == "OPTIONS") {
            output.writeHeaders(204, "No Content", mapOf("Content-Length" to "0"))
            output.flush()
            return
        }
        val route = routes[path]
        if (route == null) {
            output.writeTextResponse(404, "Not Found", "Not found")
            return
        }
        when (route) {
            is ConstantRoute -> {
                output.writeHeaders(
                    200,
                    "OK",
                    mapOf(
                        "Content-Type" to route.contentType,
                        "Content-Length" to route.bytes.size.toString(),
                    ),
                )
                if (method != "HEAD") output.write(route.bytes)
                output.flush()
            }
            is ProxyRoute -> proxy(route, method, headers, output)
            is HlsRoute -> serveHlsPlaylist(route, method, output)
        }
    }

    private fun serveHlsPlaylist(route: HlsRoute, method: String, output: BufferedOutputStream) {
        val content = readAll(route.upstreamUrl, route.dataSourceFactory, route.requestHeaders)
            .toString(StandardCharsets.UTF_8)
        val rewritten = content.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#") -> HLS_URI.replace(line) { match ->
                    val upstream = URI(route.upstreamUrl).resolve(match.groupValues[1]).toString()
                    val local = if (upstream.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                        val childPath = "/hls-${UUID.randomUUID()}.m3u8"
                        routes[childPath] = route.copy(upstreamUrl = upstream)
                        localUrl(route.localAddress, childPath)
                    } else {
                        localUrl(
                            route.localAddress,
                            addProxyRoute(upstream, null, route.dataSourceFactory, route.requestHeaders),
                        )
                    }
                    "URI=\"$local\""
                }
                line.isBlank() -> line
                else -> {
                    val upstream = URI(route.upstreamUrl).resolve(line.trim()).toString()
                    if (upstream.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                        val childPath = "/hls-${UUID.randomUUID()}.m3u8"
                        routes[childPath] = route.copy(upstreamUrl = upstream)
                        localUrl(route.localAddress, childPath)
                    } else {
                        localUrl(
                            route.localAddress,
                            addProxyRoute(upstream, null, route.dataSourceFactory, route.requestHeaders),
                        )
                    }
                }
            }
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
        val range = parseRange(requestHeaders["range"])
        val source = (route.dataSourceFactory ?: defaultFactory(route.requestHeaders)).createDataSource()
        route.requestHeaders.forEach(source::setRequestProperty)
        val spec = DataSpec.Builder()
            .setUri(route.upstreamUrl)
            .setPosition(range?.first ?: 0L)
            .apply {
                range?.last
                    ?.takeIf { it != Long.MAX_VALUE }
                    ?.let { end -> setLength(end - range.first + 1L) }
            }
            .build()
        try {
            val length = source.open(spec)
            val responseCode = source.responseCode
            val partial = range != null || responseCode == 206
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
                headers["Content-Range"] = responseHeaders.headerValue("Content-Range")
                    ?: buildString {
                        val start = range?.first ?: 0L
                        append("bytes $start-")
                        append(if (length == C.LENGTH_UNSET.toLong()) "*" else start + length - 1L)
                        append("/*")
                    }
            }
            output.writeHeaders(if (partial) 206 else 200, if (partial) "Partial Content" else "OK", headers)
            if (method != "HEAD") {
                val buffer = ByteArray(PROXY_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
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

    private fun BufferedInputStream.readHttpLine(): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < MAX_HEADER_LINE) {
            val value = read()
            if (value == -1) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.US_ASCII)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        return bytes.toString(StandardCharsets.US_ASCII)
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 30_000
        const val PROXY_BUFFER_SIZE = 64 * 1024
        const val MAX_HEADER_LINE = 16 * 1024
        val DASH_URL = Regex("https?://[^\\s<>\\\"]+")
        val HLS_URI = Regex("URI=\\\"([^\\\"]+)\\\"")

        fun String.xmlUnescape(): String = replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

        fun parseRange(value: String?): LongRange? {
            val raw = value?.removePrefix("bytes=") ?: return null
            val start = raw.substringBefore('-').toLongOrNull() ?: return null
            val end = raw.substringAfter('-', "").toLongOrNull() ?: Long.MAX_VALUE
            return start..end
        }

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
