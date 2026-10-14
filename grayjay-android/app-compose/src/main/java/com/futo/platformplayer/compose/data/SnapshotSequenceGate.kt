package com.futo.platformplayer.compose.data

/** Main-thread publication gate for off-thread snapshots which can finish out of order. */
internal class SnapshotSequenceGate {
    private var latest = Long.MIN_VALUE
    fun accept(sequence: Long): Boolean {
        if (sequence <= latest) return false
        latest = sequence
        return true
    }
}
