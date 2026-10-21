package com.futo.platformplayer.compose

/** A cancelled non-cooperative backend callback may still arrive; it no longer owns UI state. */
internal class RequestEpoch {
    @Volatile private var value = 0L
    fun next(): Long = ++value
    fun current(): Long = value
    fun isCurrent(token: Long): Boolean = token == value
}
