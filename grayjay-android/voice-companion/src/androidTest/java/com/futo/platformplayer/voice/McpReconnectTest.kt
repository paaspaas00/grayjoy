package com.futo.platformplayer.voice

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Read-only calls against the paired test app; never starts playback or edits its library. */
class McpReconnectTest {
    @Test fun repeatedReconnectsAndConcurrentStatusCallsUseOneLiveSession(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        GrayjoyMcpClient(context).use { client ->
            repeat(4) {
                client.connect()
                assertTrue(client.tools().contains("pause"))
                val results = (0 until 12).map { async(Dispatchers.Default) { client.call("status") } }.awaitAll()
                assertEquals(12, results.size)
                assertTrue(results.all { it.length() > 0 })
                client.close()
            }
        }
    }
}
