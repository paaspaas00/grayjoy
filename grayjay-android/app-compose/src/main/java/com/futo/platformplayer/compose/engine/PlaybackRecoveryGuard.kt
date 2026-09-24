package com.futo.platformplayer.compose.engine

/** READY is not evidence of recovery: a broken source may refill and fail at the same position. */
internal class PlaybackRecoveryGuard {
    private var videoId: String? = null
    private var positionMs = 0L
    private var failures = 0

    fun recordFailure(id: String, position: Long, networkAvailable: Boolean): Boolean {
        val safePosition = position.coerceAtLeast(0L)
        val distance = if (safePosition >= positionMs) safePosition - positionMs else positionMs - safePosition
        if (id != videoId || distance > 5_000L) {
            videoId = id
            positionMs = safePosition
            failures = 0
        }
        if (!networkAvailable) {
            failures = 0
            return false
        }
        failures = (failures + 1).coerceAtMost(2)
        return failures >= 2
    }

    fun reset() { videoId = null; failures = 0; positionMs = 0 }
}
