package com.futo.platformplayer.voice

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class VoiceModelTransferTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedPromotionRestoresThePreviousModel() {
        val root = temporary.newFolder()
        val target = File(root, "voice").apply { mkdir(); resolve("model").writeText("old") }
        val staging = File(root, "voice.staging").apply { mkdir(); resolve("model").writeText("new") }
        assertTrue(runCatching {
            installVoiceModelDirectory(staging, target) { from, to -> from != staging && from.renameTo(to) }
        }.isFailure)
        assertEquals("old", target.resolve("model").readText())
        assertEquals("new", staging.resolve("model").readText())
        assertFalse(File(root, "voice.previous").exists())
    }

    @Test fun interruptedPromotionIsRecoveredBeforeTheNextAccess() {
        val root = temporary.newFolder()
        val target = File(root, "voice")
        File(root, "voice.previous").apply { mkdir(); resolve("model").writeText("old") }
        assertEquals(target, recoverVoiceModelDirectory(target))
        assertEquals("old", target.resolve("model").readText())
        assertFalse(File(root, "voice.previous").exists())
    }

    @Test fun successfulPromotionReplacesOnlyTheRequestedDirectory() {
        val root = temporary.newFolder()
        val other = File(root, "other").apply { mkdir(); resolve("model").writeText("keep") }
        val target = File(root, "voice").apply { mkdir(); resolve("model").writeText("old") }
        val staging = File(root, "voice.staging").apply { mkdir(); resolve("model").writeText("new") }
        installVoiceModelDirectory(staging, target)
        assertEquals("new", target.resolve("model").readText())
        assertEquals("keep", other.resolve("model").readText())
        assertFalse(staging.exists())
        assertFalse(File(root, "voice.previous").exists())
    }

    @Test fun cancellationInterruptsWaitingForResponseHeaders(): Unit = cancellationInterruptsDownload(headers = false)
    @Test fun cancellationInterruptsAStalledResponseBody(): Unit = cancellationInterruptsDownload(headers = true)

    private fun cancellationInterruptsDownload(headers: Boolean): Unit = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val ready = CompletableDeferred<Unit>()
            val peer = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    if (headers) {
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n".toByteArray())
                        socket.getOutputStream().flush()
                    } else ready.complete(Unit)
                    assertEquals(-1, input.read())
                }
            }
            val call = OkHttpClient.Builder().readTimeout(1, TimeUnit.MINUTES).build()
                .newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/model").build())
            val download = launch {
                withCancellableVoiceDownload(call) { response ->
                    ready.complete(Unit)
                    response.body.bytes()
                }
            }
            withTimeout(5_000L) { ready.await() }
            withTimeout(2_000L) { download.cancelAndJoin(); peer.join() }
            assertTrue(call.isCanceled())
        }
    }
}
