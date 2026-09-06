package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.engine.queueInsertionIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class QueuePreparationGateTest {
    @Test
    fun synchronousOfflineResolutionCannotReenterQueuePreparation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = QueuePreparationGate(scope)
        val emitted = mutableListOf<String>()
        try {
            assertTrue(gate.launch {
                for (id in listOf("middle", "next", "last")) {
                    assertTrue(gate.isRunning)
                    emitted += id
                    assertFalse(gate.launch { emitted += "reentered" })
                }
            })
            assertEquals(listOf("middle", "next", "last"), emitted)
            assertFalse(gate.isRunning)
            assertTrue(gate.launch { emitted += "new-session" })
        } finally { scope.cancel() }
    }

    @Test
    fun lateResolutionPreservesForwardOrderFromMiddleOfPlaylist() {
        val order = playlistQueueFrom(listOf("one", "two", "three", "four", "five"), "three")
        val prepared = mutableListOf("three")
        for (id in listOf("five", "four")) prepared.add(queueInsertionIndex(prepared, order, id), id)
        assertEquals(listOf("three", "four", "five"), prepared)
    }

    @Test
    fun playNextOrderWinsEvenWhenAnotherItemFinishesResolvingFirst() {
        val order = listOf("current", "play-next", "already-resolving")
        val prepared = mutableListOf("current", "already-resolving")
        prepared.add(queueInsertionIndex(prepared, order, "play-next"), "play-next")
        assertEquals(order, prepared)
    }
}
