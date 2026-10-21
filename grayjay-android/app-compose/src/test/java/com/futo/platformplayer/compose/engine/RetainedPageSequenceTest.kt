package com.futo.platformplayer.compose.engine

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RetainedPageSequenceTest {
    private data class Page(val items: List<String>, val next: String?)

    @Test fun endingFirstSourceDoesNotDiscardSecondSourceContinuation() = runBlocking {
        val calls = mutableListOf<String>()
        val sequence = RetainedPageSequence(this, listOf("np:first", "plugin:first"), {
            calls += it
            when (it) {
                "np:first" -> Page(listOf("a"), null)
                "plugin:first" -> Page(listOf("b"), "plugin:second")
                else -> Page(listOf("c"), null)
            }
        }, Page::next)
        try {
            assertEquals(listOf("a", "b"), sequence.next().flatMap { it.items })
            assertTrue(sequence.hasMore)
            assertEquals(listOf("c"), sequence.next().flatMap { it.items })
            assertFalse(sequence.hasMore)
            assertEquals(listOf("np:first", "plugin:first", "plugin:second"), calls)
        } finally { sequence.close() }
    }

    @Test fun failedSiblingRetriesWithoutConsumingSuccessfulSourceAgain() = runBlocking {
        val calls = mutableMapOf<String, Int>()
        val sequence = RetainedPageSequence(this, listOf("plugin:youtube-fallback", "plugin:other"), {
            val count = (calls[it] ?: 0) + 1
            calls[it] = count
            if (it == "plugin:other" && count == 1) error("temporary failure")
            Page(listOf(it), null)
        }, Page::next)
        try {
            assertTrue(runCatching { sequence.next() }.isFailure)
            assertEquals(2, sequence.next().flatMap { it.items }.size)
            assertEquals(1, calls["plugin:youtube-fallback"])
            assertEquals(2, calls["plugin:other"])
        } finally { sequence.close() }
    }

    @Test fun cancellationDoesNotLoseInFlightPageAndEvictionStopsWork() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        var loads = 0
        val sequence = RetainedPageSequence(this, listOf("one"), {
            loads++; ready.await(); Page(listOf("retained"), null)
        }, Page::next)
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { sequence.next() }
        yield()
        caller.cancelAndJoin()
        ready.complete(Unit)
        try {
            assertEquals(listOf("retained"), sequence.next().single().items)
            assertEquals(1, loads)
        } finally { sequence.close() }

        supervisorScope {
            val pending = RetainedPageSequence<Page>(this, listOf("blocked"), { awaitCancellation() }, Page::next)
            val waiting = async { pending.next() }
            yield()
            pending.close()
            // Cache expiry is a recoverable page error, not a cancellation of the UI caller;
            // otherwise the screen can retain its loading indicator forever.
            assertTrue(runCatching { waiting.await() }.exceptionOrNull() is IllegalStateException)
        }
    }

    @Test fun evictedAndClearedSessionsAreClosed() {
        class Handle : AutoCloseable { var closed = false; override fun close() { closed = true } }
        val registry = BoundedSessionRegistry<Handle>(2)
        val first = Handle(); val second = Handle(); val third = Handle()
        registry["first"] = first; registry["second"] = second
        assertSame(first, registry["first"])
        registry["third"] = third
        assertTrue(second.closed); assertFalse(first.closed)
        registry.clear()
        assertTrue(first.closed); assertTrue(third.closed)
    }
}
