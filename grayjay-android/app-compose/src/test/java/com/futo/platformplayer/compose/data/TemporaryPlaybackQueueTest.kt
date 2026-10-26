package com.futo.platformplayer.compose.data

import org.junit.Assert.*
import org.junit.Test

class TemporaryPlaybackQueueTest {
    @Test fun queuePreservesPlaybackOrderWithoutCreatingAnotherCollection() {
        val queue = temporaryPlaybackQueue(null, listOf("b", "c", "a", "b"), "In coda", "session-1")
        assertEquals(listOf("b", "c", "a"), queue.videoIds)
        assertEquals("In coda", queue.title)
        assertTrue(queue.isPlaybackQueue)
        val updated = temporaryPlaybackQueue(queue, queue.videoIds + "d", "In coda", "unused")
        assertEquals(queue.id, updated.id)
        assertEquals(4, updated.videoCount)
        assertSame(updated, temporaryPlaybackQueue(updated, updated.videoIds, "In coda", "unused"))
        val nextSession = temporaryPlaybackQueue(null, listOf("x"), "In coda", "session-2")
        assertNotEquals(queue.id, nextSession.id)
    }
}
