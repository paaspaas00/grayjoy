package com.futo.platformplayer.compose.shorts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ShortsWarmGateTest {
    @Test fun bufferingCancelsTheInFlightWarmWithoutWaitingForItsTimeout() = runBlocking {
        val ready = MutableStateFlow(true)
        val window = MutableStateFlow("current")
        val started = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val result = async {
            runShortsWarmWhileReady(ready, window, { ready.value && window.value == "current" }) {
                try { started.complete(Unit); awaitCancellation() }
                finally { closed.complete(Unit) }
            }
        }
        withTimeout(1_000) { started.await() }
        ready.value = false
        assertFalse(withTimeout(1_000) { result.await() })
        withTimeout(1_000) { closed.await() }
    }

    @Test fun changingTheVisibleShortCancelsItsOldTransferAndDoesNotStartWhenUnready() = runBlocking {
        val ready = MutableStateFlow(true)
        val window = MutableStateFlow("first")
        val started = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val result = async {
            runShortsWarmWhileReady(ready, window, { ready.value && window.value == "first" }) {
                try { started.complete(Unit); awaitCancellation() }
                finally { closed.complete(Unit) }
            }
        }
        withTimeout(1_000) { started.await() }
        window.value = "second"
        assertFalse(withTimeout(1_000) { result.await() })
        withTimeout(1_000) { closed.await() }
        assertFalse(runShortsWarmWhileReady(ready, window, { false }) { fail("Must not start") })
    }

    @Test fun sessionCancellationStillPropagatesAndClosesTheTransfer() = runBlocking {
        val ready = MutableStateFlow(true)
        val window = MutableStateFlow(0)
        val started = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val result = async {
            runShortsWarmWhileReady(ready, window, { true }) {
                try { started.complete(Unit); awaitCancellation() }
                finally { closed.complete(Unit) }
            }
        }
        withTimeout(1_000) { started.await() }
        result.cancelAndJoin()
        withTimeout(1_000) { closed.await() }
        assertTrue(result.isCancelled)
    }
}
