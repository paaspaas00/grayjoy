package com.futo.platformplayer.compose.util

import org.junit.Assert.*
import org.junit.Test

class SnapshotOrderingTest {
    @Test fun mutableTimestampsAreReadExactlyOnceBeforeSorting() {
        val calls = IntArray(5000)
        val random = java.util.Random(61)
        val ordered = snapshotSortedBy((calls.indices).toList()) { index ->
            calls[index]++
            check(calls[index] == 1) { "Timestamp read again from comparator" }
            random.nextLong()
        }
        assertEquals(calls.size, ordered.size)
        assertEquals(calls.size, ordered.toSet().size)
        assertTrue(calls.all { it == 1 })
    }
    @Test fun equalKeysRetainOriginalOrder() {
        assertEquals(listOf("a", "b", "c"), snapshotSortedBy(listOf("a", "b", "c")) { 1L })
    }
}
