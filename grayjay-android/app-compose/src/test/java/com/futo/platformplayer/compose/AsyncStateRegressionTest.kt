package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.data.SnapshotSequenceGate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AsyncStateRegressionTest {
    @Test fun outOfOrderSnapshotsNeverRollStateBack() {
        repeat(40) { seed ->
            val gate = SnapshotSequenceGate()
            var largest = Long.MIN_VALUE
            (0L..500L).shuffled(Random(seed)).forEach { sequence ->
                val accepted = gate.accept(sequence)
                assertEquals(sequence > largest, accepted)
                if (accepted) largest = sequence
                assertFalse(gate.accept(sequence))
            }
            assertEquals(500L, largest)
        }
    }

    @Test fun staleBackendCallbacksLoseOwnershipEvenWhenThePageLooksTheSame() {
        val epoch = RequestEpoch()
        val previous = epoch.next()
        val current = epoch.next()
        assertFalse(epoch.isCurrent(previous))
        assertTrue(epoch.isCurrent(current))
        epoch.next()
        assertFalse(epoch.isCurrent(current))
    }

    @Test fun queueRequestsRunInOrderAndCancellingDropsAllPendingRequests() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = SerialMutationQueue(scope)
        val blocker = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        try {
            queue.launch { blocker.await(); calls += 0 }
            repeat(100) { id -> queue.launch { calls += id + 1 } }
            queue.cancel()
            blocker.complete(Unit)
            yield()
            assertTrue(calls.isEmpty())
            repeat(100) { id -> queue.launch { calls += id } }
            assertEquals((0 until 100).toList(), calls)
        } finally { scope.cancel() }
    }

    @Test fun queueNeverStartsWorkInAnAlreadyCancelledScope() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.cancel()
        val queue = SerialMutationQueue(scope)
        repeat(100) { queue.launch { error("Cancelled scope ran work") } }
        queue.cancel()
    }
}
