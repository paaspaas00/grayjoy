package com.futo.platformplayer.voice

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class McpSocketSessionTest {
    @Test fun concurrentCallsCannotMixRequestIdsOrResponses(): Unit = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val peer = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    val output = socket.getOutputStream().bufferedWriter()
                    repeat(60) {
                        val request = JSONObject(input.readLine())
                        val response = JSONObject().put("jsonrpc", "2.0").put("id", request.getInt("id"))
                            .put("result", request.getJSONObject("params"))
                        output.write(response.toString()); output.newLine(); output.flush()
                    }
                }
            }
            McpSocketSession(Socket("127.0.0.1", server.localPort), "fixture").use { client ->
                val results = (0 until 60).map { id -> async(Dispatchers.Default) {
                    client.rpc("test", JSONObject().put("value", id)).getInt("value")
                } }.awaitAll()
                assertEquals((0 until 60).toList(), results)
            }
            peer.join()
        }
    }

    @Test fun cancellationInterruptsAReadInsteadOfWaitingForSocketTimeout(): Unit = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val received = CompletableDeferred<Unit>()
            val peer = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    input.readLine(); received.complete(Unit)
                    assertEquals(-1, input.read())
                }
            }
            McpSocketSession(Socket("127.0.0.1", server.localPort).apply { soTimeout = 35_000 }, "fixture").use { client ->
                val call = launch { client.rpc("wait", JSONObject()) }
                withTimeout(3_000) { received.await() }
                withTimeout(2_000) { call.cancelAndJoin(); peer.join() }
                assertTrue(client.isClosed)
            }
        }
    }

    @Test fun framingRejectsTruncationAndOversizedResponses() {
        repeat(100) { size ->
            assertEquals("x".repeat(size), readMcpResponse(StringReader("x".repeat(size) + "\n"), size))
            for (invalid in listOf("x".repeat(size + 1) + "\n", "x".repeat(size))) {
                try { readMcpResponse(StringReader(invalid), size); fail("Invalid frame accepted") }
                catch (_: IllegalStateException) { }
            }
        }
    }
}
