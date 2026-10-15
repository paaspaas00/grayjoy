package com.futo.platformplayer.compose.engine

/** Loading bytes is progress too: do not replace a source which is actively filling its buffer. */
internal class PlaybackStallGuard(private val timeoutMs: Long = 12_000L) {
    private var id: String? = null
    private var position = 0L
    private var buffered = 0L
    private var changedAt = 0L

    fun stalled(videoId: String, positionMs: Long, bufferedMs: Long, nowMs: Long): Boolean {
        if (id != videoId || positionMs != position || bufferedMs != buffered) {
            id = videoId; position = positionMs; buffered = bufferedMs; changedAt = nowMs
            return false
        }
        return nowMs - changedAt >= timeoutMs
    }
}
