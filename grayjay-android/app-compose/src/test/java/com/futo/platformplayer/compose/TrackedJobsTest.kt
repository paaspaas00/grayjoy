package com.futo.platformplayer.compose

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TrackedJobsTest {
    @Test fun sharedPreparationFailureDoesNotCancelParentOrSiblingConsumers() = runBlocking {
        val expected = java.io.IOException("Simulated library write failure")
        val result = asyncJobResult { throw expected }
        repeat(3) { assertSame(expected, result.await().exceptionOrNull()) }
        assertTrue(currentCoroutineContext().isActive)
        assertEquals(42, asyncJobResult { 42 }.await().getOrThrow())
    }

    @Test fun cancellingSharedPreparationRemainsCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val result = asyncJobResult { started.complete(Unit); awaitCancellation() }
        started.await()
        result.cancelAndJoin()
        assertTrue(result.isCancelled)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancelledCleanupCannotRemoveItsReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jobs = mutableMapOf<String, Job>()
        val cleanup = CompletableDeferred<Unit>()
        try {
            scope.launchTracked(jobs, "download") {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await() } }
            }
            val replacement = scope.launchTracked(jobs, "download") { awaitCancellation() }
            cleanup.complete(Unit)
            assertSame(replacement, jobs["download"])
            replacement.cancel()
            assertTrue(jobs.isEmpty())
        } finally { cleanup.complete(Unit); scope.cancel() }
    }

    @Test fun immediateCompletionAndCancelledScopesLeaveNoPhantomJobs() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jobs = mutableMapOf<String, Job>()
        scope.launchTracked(jobs, "one") {}
        assertTrue(jobs.isEmpty())
        scope.cancel()
        scope.launchTracked(jobs, "two") { error("Must not start") }
        assertTrue(jobs.isEmpty())
    }

    @Test fun fuzzCancellationAndReplacementOrder() {
        val random = Random(0x230012)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jobs = mutableMapOf<Int, Job>()
        val cleanups = mutableListOf<CompletableDeferred<Unit>>()
        try {
            repeat(5_000) {
                val key = random.nextInt(8)
                when (random.nextInt(3)) {
                    0, 1 -> {
                        val cleanup = CompletableDeferred<Unit>()
                        cleanups += cleanup
                        val job = scope.launchTracked(jobs, key) {
                            try { awaitCancellation() }
                            finally { withContext(NonCancellable) { cleanup.await() } }
                        }
                        assertSame(job, jobs[key])
                    }
                    else -> jobs.remove(key)?.cancel()
                }
                if (cleanups.isNotEmpty() && random.nextBoolean()) {
                    cleanups.removeAt(random.nextInt(cleanups.size)).complete(Unit)
                }
                assertTrue(jobs.size <= 8)
                assertTrue(jobs.values.all { it.isActive })
            }
        } finally {
            scope.cancel()
            cleanups.forEach { it.complete(Unit) }
        }
        assertTrue(jobs.isEmpty())
    }
}
