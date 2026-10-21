package com.futo.platformplayer.backend

import com.futo.platformplayer.api.media.structures.IPager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UncommittedPluginPageTest {
    @Test fun cancelledNextPageCanBeRetriedWithoutSkippingItsItems() = runBlocking {
        val emitted = linkedSetOf("first")
        var page = listOf("first")
        lateinit var task: Job
        val pager = object : IPager<String> {
            override fun hasMorePages() = page == listOf("first")
            override fun getResults() = page
            override fun nextPage() { page = listOf("second", "third"); task.cancel() }
        }
        task = launch(start = CoroutineStart.LAZY) {
            val staged = readUncommittedPluginPage(pager, emitted, 30, Any::toString)
            emitted.addAll(staged.keys)
        }
        task.start(); task.join()
        assertEquals(setOf("first"), emitted)
        val retry = readUncommittedPluginPage(pager, emitted, 30, Any::toString)
        assertEquals(listOf("second", "third"), retry.items)
        assertFalse(retry.hasMore)
    }

    @Test fun transientPageFailureDoesNotPermanentlyExhaustThePager() = runBlocking {
        var fail = true
        var page = listOf("first")
        val pager = object : IPager<String> {
            override fun hasMorePages() = page == listOf("first")
            override fun getResults() = page
            override fun nextPage() {
                if (fail) { fail = false; throw IOException("Temporary disconnect") }
                page = listOf("second")
            }
        }
        val emitted = setOf("first")
        assertTrue(runCatching { readUncommittedPluginPage(pager, emitted, 30, Any::toString) }.exceptionOrNull() is IOException)
        assertEquals(listOf("second"), readUncommittedPluginPage(pager, emitted, 30, Any::toString).items)
    }

    @Test fun stagesSmallBatchesWithoutMarkingUnreadItemsAsDelivered() = runBlocking {
        val pager = object : IPager<String> {
            override fun hasMorePages() = false
            override fun getResults() = listOf("one", "one", "two", "three")
            override fun nextPage() = error("No network page exists")
        }
        val emitted = linkedSetOf<String>()
        val first = readUncommittedPluginPage(pager, emitted, 2, Any::toString)
        assertEquals(listOf("one", "two"), first.items)
        assertTrue(first.hasMore)
        assertTrue(emitted.isEmpty())
        emitted.addAll(first.keys)
        val last = readUncommittedPluginPage(pager, emitted, 2, Any::toString)
        assertEquals(listOf("three"), last.items)
        assertFalse(last.hasMore)
    }
}
