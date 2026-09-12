package com.futo.platformplayer.compose.engine

/** Leave room for extraction, Compose and artwork within the app's Java heap. */
internal fun playbackBufferBudgetBytes(maxHeapBytes: Long): Int =
    (maxHeapBytes.coerceAtLeast(1L) / 8L)
        .coerceIn(4L * 1024 * 1024, 48L * 1024 * 1024)
        .toInt()

@androidx.media3.common.util.UnstableApi
internal fun createPlaybackLoadControl(maxHeapBytes: Long) =
    androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setTargetBufferBytes(playbackBufferBudgetBytes(maxHeapBytes))
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()
