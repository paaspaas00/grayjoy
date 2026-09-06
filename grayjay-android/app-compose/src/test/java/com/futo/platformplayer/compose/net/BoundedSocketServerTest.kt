package com.futo.platformplayer.compose.net

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BoundedSocketServerTest {
    @Test
    fun serverCanStopAndRestartWithoutReusingShutdownExecutor() {
        val server = BoundedSocketServer("restart-test") { it.getOutputStream().write(42) }
        try {
            repeat(3) {
                server.start()
                Socket(InetAddress.getLoopbackAddress(), server.port).use { client ->
                    client.soTimeout = 2_000
                    assertEquals(42, client.getInputStream().read())
                }
                server.stop()
                assertEquals(0, server.port)
            }
        } finally { server.stop() }
    }

    @Test
    fun excessConnectionsAreClosedInsteadOfGrowingThreads() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = BoundedSocketServer("limit-test", workerCount = 1, pendingCount = 1) {
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
        }
        val clients = mutableListOf<Socket>()
        try {
            server.start()
            clients += Socket(InetAddress.getLoopbackAddress(), server.port)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            clients += Socket(InetAddress.getLoopbackAddress(), server.port)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.connectedClientCount < 2 && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(2, server.connectedClientCount)
            Socket(InetAddress.getLoopbackAddress(), server.port).use { rejected ->
                rejected.soTimeout = 2_000
                assertEquals(-1, rejected.getInputStream().read())
            }
        } finally {
            release.countDown()
            server.stop()
            clients.forEach { it.close() }
        }
    }

    @Test
    fun stopUnblocksAClientReadAndReleasesTheSocket() {
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val server = BoundedSocketServer("close-test") {
            try { entered.countDown(); it.getInputStream().read() } finally { exited.countDown() }
        }
        try {
            server.start()
            Socket(InetAddress.getLoopbackAddress(), server.port).use {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                server.stop()
                assertTrue(exited.await(2, TimeUnit.SECONDS))
                assertEquals(0, server.connectedClientCount)
            }
        } finally { server.stop() }
    }
}
