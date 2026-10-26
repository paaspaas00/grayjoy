package com.futo.platformplayer.compose.ui.screens

internal sealed interface PlayerTapAction {
    data object ToggleControls : PlayerTapAction
    data class Seek(val direction: Int, val accumulatedSeconds: Int) : PlayerTapAction
}

/** Tap actions happen on release. Quick seek has its own continuation window and side zones. */
internal class PlayerTapSequence {
    private var lastTapAt: Long? = null
    private var lastSide = 0
    private var quickSeekUntil = Long.MIN_VALUE
    private var quickSeekSide = 0
    private var seconds = 0

    fun reset() {
        lastTapAt = null
        lastSide = 0
        quickSeekUntil = Long.MIN_VALUE
        quickSeekSide = 0
        seconds = 0
    }

    fun released(atMs: Long, xFraction: Float, canSeek: Boolean, doubleTapTimeoutMs: Long): PlayerTapAction {
        val side = when { xFraction < 1f / 3f -> -1; xFraction > 2f / 3f -> 1; else -> 0 }
        val sincePrevious = lastTapAt?.let { atMs - it }
        val continuation = atMs <= quickSeekUntil
        if (canSeek && side != 0 && (continuation ||
                (lastSide != 0 && sincePrevious != null && sincePrevious in 40..doubleTapTimeoutMs))) {
            seconds = if (continuation && side == quickSeekSide) seconds + 10 else 10
            quickSeekSide = side
            quickSeekUntil = atMs + 650L
            lastTapAt = null
            lastSide = 0
            return PlayerTapAction.Seek(side, seconds)
        }
        reset()
        if (canSeek && side != 0) { lastTapAt = atMs; lastSide = side }
        return PlayerTapAction.ToggleControls
    }
}
