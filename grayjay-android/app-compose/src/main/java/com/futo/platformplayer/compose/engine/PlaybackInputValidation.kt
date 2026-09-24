package com.futo.platformplayer.compose.engine

/** Persisted/plugin values must not pass NaN or infinity into Media3 PlaybackParameters. */
internal fun normalizedPlaybackSpeed(speed: Float): Float =
    if (speed.isFinite()) speed.coerceIn(0.25f, 3f) else 1f

/** Saturating addition keeps malformed offsets from wrapping a forward seek back to zero. */
internal fun clampedSeekPosition(currentPositionMs: Long, deltaMs: Long, durationMs: Long?): Long {
    val position = currentPositionMs.coerceAtLeast(0L)
    val target = when {
        deltaMs >= 0L -> position + minOf(deltaMs, Long.MAX_VALUE - position)
        deltaMs < -position -> 0L
        else -> position + deltaMs
    }
    return durationMs?.takeIf { it >= 0L }?.let { target.coerceAtMost(it) } ?: target
}
