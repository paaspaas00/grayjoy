package com.futo.platformplayer.compose.pclink

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class PcLinkHttpServer(
    private val manager: PcLinkManager,
) {
    private data class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "Grayjoy-PcLink").apply { isDaemon = true }
    }
    private var serverSocket: ServerSocket? = null

    @Synchronized
    fun start() {
        if (running.get()) return
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(PcLinkProtocol.PORT))
        }
        serverSocket = socket
        running.set(true)
        executor.execute {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                executor.execute {
                    runCatching { handle(client) }
                    runCatching(client::close)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val request = input.readRequest() ?: return
        if (request.method == "OPTIONS") {
            output.writeResponse(204, "No Content", ByteArray(0))
            return
        }
        val path = request.target.substringBefore('?')
        if (path !in SUPPORTED_PATHS) {
            output.writeJson(404, JSONObject().put("error", "not_found"))
            return
        }
        val computerId = request.headers[HEADER_COMPUTER].orEmpty()
        val timestamp = request.headers[HEADER_TIMESTAMP].orEmpty()
        val nonce = request.headers[HEADER_NONCE].orEmpty()
        val signature = request.headers[HEADER_SIGNATURE].orEmpty()
        if (
            computerId.isBlank() ||
            !manager.verifyRequest(
                computerId = computerId,
                timestamp = timestamp,
                nonce = nonce,
                method = request.method,
                requestTarget = request.target,
                body = request.body,
                signature = signature,
            )
        ) {
            output.writeJson(401, JSONObject().put("error", "unauthorized"))
            return
        }

        val address = socket.inetAddress?.hostAddress.orEmpty()
        manager.markSeen(computerId, address)
        when {
            request.method == "GET" && path == PATH_PAIR_STATUS -> {
                val computer = manager.pairedComputer(computerId) ?: run {
                    output.writeJson(404, JSONObject().put("error", "not_paired"))
                    return
                }
                output.writeJson(
                    200,
                    JSONObject()
                        .put("ok", true)
                        .put("protocolVersion", PcLinkProtocol.VERSION)
                        .put("computerName", computer.name)
                        .put("serverPort", PcLinkProtocol.PORT),
                )
            }
            request.method == "POST" && path == PATH_STATE -> {
                val computer = manager.pairedComputer(computerId) ?: run {
                    output.writeJson(404, JSONObject().put("error", "not_paired"))
                    return
                }
                val json = runCatching {
                    JSONObject(request.body.toString(StandardCharsets.UTF_8))
                }.getOrNull() ?: run {
                    output.writeJson(400, JSONObject().put("error", "invalid_json"))
                    return
                }
                val active = json.optBoolean("active", false)
                val state = PcPlaybackState(
                    computerId = computerId,
                    computerName = computer.name,
                    active = active,
                    kind = if (json.optString("kind") == "playlist") {
                        PcMediaKind.Playlist
                    } else {
                        PcMediaKind.Video
                    },
                    title = json.optString("title").take(MAX_TITLE_LENGTH),
                    videoTitle = json.optString("videoTitle").take(MAX_TITLE_LENGTH),
                    videoUrl = json.optString("videoUrl").take(MAX_URL_LENGTH),
                    playlistUrl = json.optString("playlistUrl").take(MAX_URL_LENGTH),
                    artworkUrl = json.optString("artworkUrl").take(MAX_URL_LENGTH),
                    isPlaying = json.optBoolean("isPlaying", false),
                    positionMs = json.optLong("positionMs").coerceAtLeast(0L),
                    durationMs = json.optLong("durationMs").coerceAtLeast(0L),
                    receivedAtMs = System.currentTimeMillis(),
                )
                manager.updatePlayback(state)
                val acknowledgedSequence = json.optLong("lastCommandSequence", 0L)
                val commandArray = JSONArray()
                manager.commandsAfter(computerId, acknowledgedSequence).forEach { command ->
                    commandArray.put(
                        JSONObject()
                            .put("sequence", command.sequence)
                            .put("type", command.type.wireName),
                    )
                }
                output.writeJson(
                    200,
                    JSONObject()
                        .put("ok", true)
                        .put("commands", commandArray),
                )
            }
            else -> output.writeJson(405, JSONObject().put("error", "method_not_allowed"))
        }
    }

    private fun BufferedInputStream.readRequest(): Request? {
        val requestLine = readHttpLine() ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        if (target.length > MAX_TARGET_LENGTH) return null
        val headers = linkedMapOf<String, String>()
        var headerBytes = requestLine.length
        while (true) {
            val line = readHttpLine() ?: return null
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES) return null
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength !in 0..MAX_BODY_BYTES) return null
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val read = read(body, offset, body.size - offset)
            if (read < 0) return null
            offset += read
        }
        return Request(method, target, headers, body)
    }

    private fun BufferedInputStream.readHttpLine(): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < MAX_HEADER_LINE) {
            val value = read()
            if (value == -1) {
                return if (bytes.size() == 0) null
                else String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
    }

    private fun BufferedOutputStream.writeJson(code: Int, json: JSONObject) {
        writeResponse(
            code = code,
            reason = when (code) {
                200 -> "OK"
                400 -> "Bad Request"
                401 -> "Unauthorized"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                else -> "Error"
            },
            body = json.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun BufferedOutputStream.writeResponse(code: Int, reason: String, body: ByteArray) {
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Private-Network: true\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Grayjoy-Computer, X-Grayjoy-Timestamp, X-Grayjoy-Nonce, X-Grayjoy-Signature\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        write(headers.toByteArray(StandardCharsets.US_ASCII))
        if (body.isNotEmpty()) write(body)
        flush()
    }

    private companion object {
        const val PATH_PAIR_STATUS = "/v1/pair/status"
        const val PATH_STATE = "/v1/state"
        val SUPPORTED_PATHS = setOf(PATH_PAIR_STATUS, PATH_STATE)
        const val HEADER_COMPUTER = "x-grayjoy-computer"
        const val HEADER_TIMESTAMP = "x-grayjoy-timestamp"
        const val HEADER_NONCE = "x-grayjoy-nonce"
        const val HEADER_SIGNATURE = "x-grayjoy-signature"
        const val SOCKET_TIMEOUT_MS = 10_000
        const val MAX_TARGET_LENGTH = 2_048
        const val MAX_HEADER_LINE = 8 * 1_024
        const val MAX_HEADER_BYTES = 32 * 1_024
        const val MAX_BODY_BYTES = 64 * 1_024
        const val MAX_TITLE_LENGTH = 500
        const val MAX_URL_LENGTH = 4_096
    }
}
