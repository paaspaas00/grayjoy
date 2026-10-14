package com.futo.platformplayer.compose.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.curlbind.Libcurl
import com.futo.platformplayer.backend.NativeHttpRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativeHttpRuntimeTest {
    @Test fun packagedNativeRuntimeCanCompleteARequest(): Unit = runBlocking {
        NativeHttpRuntime.ensureInitialized(ApplicationProvider.getApplicationContext<Context>())
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 10_000
            val reply = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                }
            }
            try {
                val response = Libcurl.perform(Libcurl.Request("http://127.0.0.1:${server.localPort}/", timeoutMs = 5_000))
                assertEquals(200, response.status)
                assertEquals("ok", response.bodyBytes.toString(Charsets.UTF_8))
                reply.get(10, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun nativeHttpsUsesAndroidTrustStore() = runBlocking {
        NativeHttpRuntime.ensureInitialized(ApplicationProvider.getApplicationContext<Context>())
        val response = Libcurl.perform(Libcurl.Request("https://example.com/", timeoutMs = 15_000))
        assertEquals(200, response.status)
        assertTrue(response.bodyBytes.isNotEmpty())
    }
}
