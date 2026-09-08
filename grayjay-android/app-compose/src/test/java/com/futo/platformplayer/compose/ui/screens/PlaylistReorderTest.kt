package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class PlaylistReorderTest {
    @Test fun `slot only changes after crossing a neighbour midpoint and margin`() {
        assertEquals(0, reorderSlotDelta(90f, 30f, 150f, 5f))
        assertEquals(0, reorderSlotDelta(154f, 30f, 150f, 5f))
        assertEquals(1, reorderSlotDelta(156f, 30f, 150f, 5f))
        assertEquals(-1, reorderSlotDelta(24f, 30f, 150f, 5f))
    }
    @Test fun `slot commit inserts exactly once and handles removal during dragging`() {
        val original = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), commitReorderSlot(original, "a", 2))
        assertEquals(listOf("c", "a", "b"), commitReorderSlot(original, "c", -1))
        assertEquals(listOf("a", "b", "c"), original)
        assertEquals(original, commitReorderSlot(original, "deleted", 1))
    }
    @Test fun `drag in either direction preserves every ID exactly once`() {
        assertEquals(listOf("b", "c", "a"), moveReorderItem(listOf("a", "b", "c"), "a", "c"))
        assertEquals(listOf("c", "a", "b"), moveReorderItem(listOf("a", "b", "c"), "c", "a"))
    }
    @Test fun `removed drag targets are a safe no-op`() {
        assertEquals(listOf("a", "b"), moveReorderItem(listOf("a", "b"), "gone", "b"))
        assertEquals(listOf("a", "b"), moveReorderItem(listOf("a", "b"), "a", "gone"))
    }
    @Test fun `concurrent additions and deletions preserve the users draft order`() {
        assertEquals(listOf("c", "a", "new"), reconcileReorderDraft(listOf("c", "b", "a"), listOf("a", "c", "new")))
        assertEquals(listOf("b", "a"), reconcileReorderDraft(listOf("b", "a", "a"), listOf("a", "b", "a")))
    }
}
