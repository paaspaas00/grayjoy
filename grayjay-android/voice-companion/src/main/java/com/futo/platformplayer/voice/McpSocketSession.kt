package com.futo.platformplayer.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One ordered stream. Cancellation closes the socket, including a blocking read. */
internal class McpSocketSession(private val socket: Socket, private val token: String) : AutoCloseable {
    private val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
    private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val lock = Mutex()
    private var requestId = 0
    val isClosed get() = socket.isClosed

    suspend fun rpc(method: String, parameters: JSONObject): JSONObject = lock.withLock {
        coroutineScope {
            suspendCancellableCoroutine { continuation ->
                val worker = launch(Dispatchers.IO) {
                    try {
                        val id = ++requestId
                        val request = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method)
                            .put("params", parameters).put("_auth", token)
                        writer.write(request.toString()); writer.newLine(); writer.flush()
                        val response = JSONObject(readMcpResponse(reader))
                        check(response.optString("jsonrpc") == "2.0" && response.optLong("id", -1) == id.toLong())
                        response.optJSONObject("error")?.let { error(it.optString("message", "MCP error")) }
                        continuation.resume(response.getJSONObject("result"))
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
                continuation.invokeOnCancellation { close(); worker.cancel() }
            }
        }
    }

    override fun close() { runCatching { socket.close() } }
}

internal fun readMcpResponse(reader: java.io.Reader, limit: Int = 32_768): String {
    val line = StringBuilder()
    while (true) {
        val next = reader.read()
        check(next >= 0) { "Grayjoy disconnected before completing its response" }
        if (next == 10) return line.toString()
        check(line.length < limit) { "MCP response too large" }
        line.append(next.toChar())
    }
}
