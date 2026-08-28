package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDetailQueueKeyTest {
    @Test
    fun duplicateVideoIdsStillProduceUniqueLazyListKeys() {
        val ids = listOf("same-video", "same-video", "another-video")

        val keys = ids.mapIndexed { index, id -> lazyVideoItemKey("queue", index, id) }

        assertEquals(keys.size, keys.distinct().size)
    }
}
