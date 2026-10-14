package com.futo.platformplayer.voice

import android.content.*
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface VoiceCommandClient {
    suspend fun call(name: String, arguments: JSONObject = JSONObject()): JSONObject
}

internal class GrayjoyMcpClient(private val context: Context) : AutoCloseable, VoiceCommandClient {
    private val operations = Mutex()
    @Volatile private var binding: Binding? = null

    private inner class Binding : AutoCloseable {
        @Volatile var closed = false
        private var connection: ServiceConnection? = null
        private var socket: Socket? = null
        var transport: McpSocketSession? = null

        @Synchronized fun bind(connection: ServiceConnection) {
            check(!closed)
            check(context.bindService(Intent().setComponent(ComponentName(BuildConfig.GRAYJOY_PACKAGE,
                "com.futo.platformplayer.compose.voice.VoiceBridgeService")), connection, Context.BIND_AUTO_CREATE))
            this.connection = connection
        }
        @Synchronized fun attach(socket: Socket) {
            if (closed) { socket.close(); error("Connection was closed") }
            this.socket = socket
        }
        @Synchronized override fun close() {
            closed = true
            runCatching { socket?.close() }; socket = null
            connection?.let { runCatching { context.unbindService(it) } }; connection = null
        }
    }

    suspend fun connect() { operations.withLock { connected() } }

    private suspend fun connected(): Binding {
        binding?.takeIf { !it.closed && it.transport?.isClosed == false }?.let { return it }
        val next = synchronized(this) { binding?.close(); Binding().also { binding = it } }
        try {
            val address = withContext(Dispatchers.Main) { withTimeout(5_000) {
                suspendCancellableCoroutine<Pair<Int, String>> { continuation ->
                    val reply = Messenger(object : Handler(Looper.getMainLooper()) {
                        override fun handleMessage(message: Message) {
                            if (message.what == 1 && continuation.isActive) continuation.resume(
                                message.data.getInt("port") to message.data.getString("token").orEmpty())
                        }
                    })
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            if (!continuation.isActive || next.closed) return
                            runCatching { Messenger(service).send(Message.obtain(null, 1).apply { replyTo = reply }) }
                                .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
                        }
                        override fun onNullBinding(name: ComponentName) {
                            if (continuation.isActive) continuation.resumeWithException(
                                IllegalStateException("Abilita il controllo vocale nelle Prefs di Grayjoy"))
                        }
                        override fun onServiceDisconnected(name: ComponentName) { next.close() }
                        override fun onBindingDied(name: ComponentName) { next.close() }
                    }
                    continuation.invokeOnCancellation { next.close() }
                    try { if (continuation.isActive) next.bind(connection) }
                    catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            } }
            require(address.first in 1024..65535 && address.second.length == 64)
            withContext(Dispatchers.IO) {
                val socket = Socket()
                next.attach(socket)
                socket.connect(InetSocketAddress("127.0.0.1", address.first), 2_000)
                socket.soTimeout = 35_000
                next.transport = McpSocketSession(socket, address.second)
            }
            next.transport!!.rpc("initialize", JSONObject().put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject()).put("clientInfo", JSONObject().put("name", "Grayjoy Voice").put("version", "0.1")))
            return next
        } catch (error: Exception) { next.close(); throw error }
    }

    suspend fun tools(): String = operations.withLock {
        val current = connected()
        try {
            val tools = current.transport!!.rpc("tools/list", JSONObject()).getJSONArray("tools")
            (0 until tools.length()).mapNotNull { index ->
                tools.getJSONObject(index).takeUnless { it.getString("name") == "voice_volume" }?.let {
                    it.getString("name") + ": " + it.getString("description").replace(';', ',')
                }
            }.joinToString("; ")
        } catch (error: Exception) { current.close(); throw error }
    }

    override suspend fun call(name: String, arguments: JSONObject): JSONObject = operations.withLock {
        val current = connected()
        try {
            val result = current.transport!!.rpc("tools/call", JSONObject().put("name", name).put("arguments", arguments))
            JSONObject(result.getJSONArray("content").getJSONObject(0).getString("text"))
        } catch (error: Exception) { current.close(); throw error }
    }

    @Synchronized override fun close() { binding?.close(); binding = null }
}
