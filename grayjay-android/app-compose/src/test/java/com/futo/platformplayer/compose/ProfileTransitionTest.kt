package com.futo.platformplayer.compose

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProfileTransitionTest {
    @Test fun deletedDestinationDuringSwitchRestoresPreviousEngine() = runBlocking {
        var engine = "old"
        var published = "old"
        val error = IllegalStateException("destination deleted while loading")
        val caught = runCatching {
            commitProfileTransition("old", "new", { engine = it }, { throw error })
            published = "new"
        }.exceptionOrNull()
        assertSame(error, caught)
        assertEquals("old", engine)
        assertEquals("old", published)
    }
    @Test fun cancellationCannotInterruptRollback() = runBlocking {
        var engine = "old"
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            commitProfileTransition("old", "new", {
                if (it == "new") { engine = it; awaitCancellation() }
                else { delay(1L); engine = it }
            }, { error("cancelled switch must not commit") })
        }
        job.cancelAndJoin()
        assertEquals("old", engine)
    }
    @Test fun successfulSwitchPublishesOnlyAfterEngineIsReady() = runBlocking {
        val calls = mutableListOf<String>()
        commitProfileTransition("old", "new", { calls += "engine:$it" }, { calls += "persist:$it" })
        assertEquals(listOf("engine:new", "persist:new"), calls)
    }
}
