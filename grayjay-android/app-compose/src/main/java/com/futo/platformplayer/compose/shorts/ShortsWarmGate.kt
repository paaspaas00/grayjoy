package com.futo.platformplayer.compose.shorts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Stop speculative bytes immediately when foreground playback needs the connection again. */
internal suspend fun runShortsWarmWhileReady(
    playbackChanges: Flow<*>,
    windowChanges: Flow<*>,
    ready: () -> Boolean,
    warm: suspend () -> Unit,
): Boolean = coroutineScope {
    if (!ready()) return@coroutineScope false
    val transfer = async(start = CoroutineStart.LAZY) { warm(); true }
    val gate = launch(start = CoroutineStart.UNDISPATCHED) {
        combine(playbackChanges, windowChanges) { _, _ -> ready() }.first { !it }
        transfer.cancel()
    }
    try {
        transfer.start()
        try {
            transfer.await()
        } catch (cancelled: CancellationException) {
            // Gate cancellation is optional work stopping, not a playback failure. Session or
            // timeout cancellation still propagates to close the transport and its entire scope.
            if (!currentCoroutineContext().isActive) throw cancelled
            false
        }
    } finally { gate.cancel() }
}
