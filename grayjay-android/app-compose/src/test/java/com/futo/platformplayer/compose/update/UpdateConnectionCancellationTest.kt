package com.futo.platformplayer.compose.update

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UpdateConnectionCancellationTest {
    @Test fun cancellingBlockedReadDisconnectsItWithoutWaitingForTheReadTimeout(): Unit = runBlocking {
        val started = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val disconnects = AtomicInteger()
        val job = async(Dispatchers.IO) {
            withUpdateConnectionCleanup(disconnect = { disconnects.incrementAndGet(); unblock.countDown() }) {
                started.countDown()
                check(unblock.await(10, TimeUnit.SECONDS)) { "Read was not disconnected" }
                42
            }
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(2_000) { job.join() }
        assertTrue(job.isCancelled)
        assertEquals(1, disconnects.get())
    }

    @Test fun normalCompletionStillClosesTheConnectionExactlyOnce(): Unit = runBlocking {
        val disconnects = AtomicInteger()
        assertEquals(42, withUpdateConnectionCleanup({ disconnects.incrementAndGet() }) { 42 })
        assertEquals(1, disconnects.get())
    }
}
