package com.futo.platformplayer.compose.voice

import android.app.Service
import android.content.Intent
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import android.os.*
import com.futo.platformplayer.compose.GrayjoyApplication
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/** MCP JSON-RPC over authenticated loopback; Android's SELinux forbids cross-UID Unix sockets. */
class VoiceBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap.newKeySet<Socket>()
    private val slots = Semaphore(2)
    private val secret = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private var server: ServerSocket? = null
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != 1 || !VoiceAccess.enabled(this@VoiceBridgeService)) return
            val packages = packageManager.getPackagesForUid(message.sendingUid).orEmpty()
            if (VoiceAccess.companionPackage(this@VoiceBridgeService) !in packages) return
            runCatching {
                message.replyTo?.send(Message.obtain(null, 1).apply { data = Bundle().apply {
                    putInt("port", server?.localPort ?: 0); putString("token", secret)
                } })
            }
        }
    })

    override fun onBind(intent: Intent): IBinder? {
        if (!VoiceAccess.enabled(this)) return null
        if (server == null) {
            val socket = try { ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")) }
            catch (error: Exception) {
                com.futo.platformplayer.compose.diagnostics.DiagnosticLog.w("VoiceBridge", "Could not open the voice command socket.", error)
                stopSelf()
                return null
            }
            server = socket
            scope.launch {
                while (isActive) {
                    val client = try { socket.accept() } catch (_: Exception) { break }
                    if (connections.size >= 2) { client.close(); continue }
                    connections.add(client)
                    launch { slots.withPermit { serve(client) } }.invokeOnCompletion {
                        // Destruction can cancel the child before serve() reaches its finally.
                        connections.remove(client)
                        runCatching { client.close() }
                    }
                }
            }
        }
        return messenger.binder
    }

    private suspend fun serve(socket: Socket) {
        val owner = UUID.randomUUID().toString()
        var touchedVolume = false
        try {
            socket.soTimeout = 5000
            val reader = socket.inputStream.bufferedReader(Charsets.UTF_8)
            val writer = socket.outputStream.bufferedWriter(Charsets.UTF_8)
            var initialized = false
            while (currentCoroutineContext().isActive && VoiceAccess.enabled(this)) {
                val line = readBoundedLine(reader) ?: break
                val request = JSONObject(line)
                if (!java.security.MessageDigest.isEqual(secret.toByteArray(), request.optString("_auth").toByteArray())) break
                if (!initialized) { initialized = true; socket.soTimeout = 0 }
                if (!request.has("id")) continue
                val response = JSONObject().put("jsonrpc", "2.0").put("id", request.get("id"))
                try {
                    require(request.optString("jsonrpc") == "2.0")
                    val result = when (request.optString("method")) {
                        "initialize" -> JSONObject().put("protocolVersion", "2025-03-26")
                            .put("capabilities", JSONObject().put("tools", JSONObject()))
                            .put("serverInfo", JSONObject().put("name", "Grayjoy").put("version", "1"))
                        "tools/list" -> JSONObject().put("tools", voiceTools())
                        "tools/call" -> {
                            val params = request.getJSONObject("params")
                            val name = params.getString("name")
                            require((0 until voiceTools().length()).any { voiceTools().getJSONObject(it).getString("name") == name })
                            val arguments = params.optJSONObject("arguments") ?: JSONObject()
                            arguments.put("_voiceSession", owner)
                            if (name == "voice_volume") touchedVolume = true
                            val output = withTimeout(30_000L) {
                                withContext(Dispatchers.Main.immediate) {
                                    (application as GrayjoyApplication).playbackViewModel.executeVoiceTool(name,
                                        arguments)
                                }
                            }
                            // Starting playback ends the listening phase. Do not leave the new
                            // track attenuated while the companion prepares spoken feedback.
                            if (output.optBoolean("ok") && !output.optBoolean("needsClarification") &&
                                name in setOf("play", "next", "previous", "play_playlist", "play_named", "search_play",
                                    "play_local", "play_random_music", "play_artist", "play_same_artist")) {
                                withContext(Dispatchers.Main.immediate) {
                                    (application as GrayjoyApplication).playbackViewModel.releaseVoiceVolume(owner)
                                }
                            }
                            JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", output.toString())))
                        }
                        else -> error("Unsupported MCP method")
                    }
                    response.put("result", result)
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    response.put("error", JSONObject().put("code", -32000).put("message", "Command timed out"))
                } catch (error: Exception) {
                    response.put("error", JSONObject().put("code", -32602).put("message", error.message?.take(200).orEmpty()))
                }
                writer.write(response.toString()); writer.newLine(); writer.flush()
            }
        } catch (_: Exception) {
            // Disconnection or a malformed frame ends only this authenticated session.
        } finally {
            connections.remove(socket); runCatching { socket.close() }
            if (touchedVolume) withContext(NonCancellable + Dispatchers.Main.immediate) {
                try { (application as GrayjoyApplication).playbackViewModel.releaseVoiceVolume(owner) }
                catch (error: Exception) {
                    com.futo.platformplayer.compose.diagnostics.DiagnosticLog.w("VoiceBridge", "Could not release a closed voice session's volume lease.", error)
                }
            }
        }
    }
    override fun onUnbind(intent: Intent): Boolean { stopSelf(); return false }
    override fun onDestroy() {
        runCatching { server?.close() }; connections.forEach { runCatching { it.close() } }
        scope.cancel(); super.onDestroy()
    }
}

internal fun readBoundedLine(reader: java.io.Reader, maximum: Int = 16_384): String? {
    val value = StringBuilder()
    while (value.length <= maximum) {
        val next = reader.read()
        if (next < 0) {
            check(value.isEmpty()) { "Truncated MCP frame" }
            return null
        }
        if (next == 10) return value.toString()
        value.append(next.toChar())
    }
    error("MCP frame too large")
}

internal fun voiceTools(): JSONArray {
    fun tool(name: String, description: String, property: String? = null, type: String = "string"): JSONObject {
        val schema = JSONObject().put("type", "object").put("additionalProperties", false)
        schema.put("properties", JSONObject().apply { if (property != null) put(property, JSONObject().put("type", type)) })
        if (property != null) schema.put("required", JSONArray().put(property))
        return JSONObject().put("name", name).put("description", description).put("inputSchema", schema)
    }
    return JSONArray(listOf(tool("status", "Current title and playback status"),
        tool("network_status", "Whether validated Internet access is currently available"),
        tool("download_audio", "Immediately enqueue audio download for a known YouTube video identifier; idempotent, leaves completed downloads intact.", "videoId"),
        tool("play_local", "Play only completed downloads, matching a song or local playlist name; never searches online.", "query"),
        tool("play_random_music", "Play random music from recent history; offline or localOnly selects only downloaded music.", "localOnly", "boolean").apply { getJSONObject("inputSchema").remove("required") },
        tool("play_artist", "Play a song by the named artist, preferring downloads and avoiding the current/recently chosen tracks.", "query").apply {
            getJSONObject("inputSchema").getJSONObject("properties").put("localOnly", JSONObject().put("type", "boolean"))
        },
        tool("play_same_artist", "Play a different song by the artist currently in context. Ask for an artist if it cannot be identified.", "localOnly", "boolean").apply {
            getJSONObject("inputSchema").remove("required")
        },
        tool("list_playlists", "Local playlist names and identifiers. Optional zero-based offset for pages of 20.", "offset", "integer").apply { getJSONObject("inputSchema").remove("required") }, tool("play", "Resume playback"),
        tool("pause", "Pause playback"), tool("next", "Next queue track"), tool("previous", "Previous queue track"),
        tool("volume", "Change music volume by -5 to 5 steps", "steps", "integer"),
        tool("voice_volume", "Assistant session playback level: 0 to 100 percent, -1 restores. Not a spoken command.", "percent", "integer"),
        tool("seek", "Seek relative seconds, from -600 to 600", "seconds", "integer"),
        tool("play_playlist", "Play a local playlist by name, sound-alike name, or identifier. Uncertain matches ask for confirmation.", "query"),
        tool("play_named", "Play a requested name: matching local playlist, downloaded recording, then relevant YouTube video with most views.", "query"),
        tool("search_play", "Find an explicitly requested YouTube title. Prefer matching downloads; otherwise choose the relevant result with most views.", "query")))
}
