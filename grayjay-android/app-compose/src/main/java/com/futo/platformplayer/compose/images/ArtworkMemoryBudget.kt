package com.futo.platformplayer.compose.images

/** Share the Java heap with the player, extractors and the Compose tree. */
internal fun artworkMemoryBudget(maxHeapBytes: Long): Pair<Long, Long> {
    val heap = maxHeapBytes.coerceAtLeast(1L)
    return (heap / 12).coerceIn(4L * 1024 * 1024, 24L * 1024 * 1024) to
        (heap / 32).coerceIn(1024L * 1024, 8L * 1024 * 1024)
}
