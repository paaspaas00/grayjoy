package com.futo.platformplayer.compose.net

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded LAN server whose stop closes blocking socket reads, and which can be restarted. */
internal class BoundedSocketServer(
    private val threadName: String,
    private val requestedPort: Int = 0,
    private val workerCount: Int = 4,
    private val pendingCount: Int = 8,
    private val handle: (Socket) -> Unit,
) {
    @Volatile private var listener: ServerSocket? = null
    private var workers: ThreadPoolExecutor? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    val port: Int get() = listener?.localPort ?: 0
    val connectedClientCount: Int get() = clients.size

    @Synchronized
    fun start() {
        if (listener != null) return
        val socket = ServerSocket()
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(requestedPort))
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
        val executor = ThreadPoolExecutor(
            workerCount, workerCount, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(pendingCount),
            { task -> Thread(task, threadName).apply { isDaemon = true } },
        ).apply { allowCoreThreadTimeOut(true) }
        workers = executor
        listener = socket
        Thread({
            while (listener === socket) {
                val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                clients.add(client)
                if (listener !== socket) {
                    close(client)
                    break
                }
                try {
                    executor.execute {
                        try { handle(client) } catch (_: Throwable) {
                            // A malformed/disconnected client must not terminate the accept loop.
                        } finally { close(client) }
                    }
                } catch (_: RejectedExecutionException) {
                    // Saturated or concurrently stopped: reject rather than spawning more threads.
                    close(client)
                }
            }
        }, "$threadName-Accept").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        val socket = listener
        listener = null
        runCatching { socket?.close() }
        clients.toList().forEach(::close)
        workers?.shutdownNow()
        workers = null
    }

    private fun close(socket: Socket) {
        clients.remove(socket)
        runCatching(socket::close)
    }
}
