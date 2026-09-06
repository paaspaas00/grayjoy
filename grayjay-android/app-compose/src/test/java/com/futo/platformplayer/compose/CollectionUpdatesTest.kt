package com.futo.platformplayer.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CollectionUpdatesTest {
    private data class Item(val id: Int, val progress: Int = 0)

    @Test
    fun unchangedFeedsKeepTheirIdentity() {
        val feed = List(10_000) { Item(it) }
        val result = feed.mapIfChanged { if (it.id == -1) it.copy(progress = 1) else it }
        assertSame(feed, result)
    }

    @Test
    fun aCheckpointReplacesOnlyItsOwnItemWithoutMutatingThePublishedSnapshot() {
        val feed = List(1_000) { Item(it) }
        val result = feed.mapIfChanged { if (it.id == 500) it.copy(progress = 42) else it }
        assertEquals(0, feed[500].progress)
        assertEquals(42, result[500].progress)
        assertEquals(feed.size, result.size)
        feed.indices.filter { it != 500 }.forEach { assertSame(feed[it], result[it]) }
    }

    @Test
    fun emptyFeedNeedsNoReplacement() {
        val feed = emptyList<Item>()
        assertSame(feed, feed.mapIfChanged { it.copy(progress = 1) })
    }
}
