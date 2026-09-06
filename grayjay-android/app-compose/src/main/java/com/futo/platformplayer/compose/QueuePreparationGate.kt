package com.futo.platformplayer.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Main-thread single-flight guard, including callbacks from immediately resolved offline items. */
internal class QueuePreparationGate(private val scope: CoroutineScope) {
    private var job: Job? = null
    val isRunning: Boolean get() = job != null

    fun launch(block: suspend CoroutineScope.() -> Unit): Boolean {
        if (isRunning) return false
        val next = scope.launch(start = CoroutineStart.LAZY, block = block)
        job = next
        next.invokeOnCompletion { if (job === next) job = null }
        next.start()
        return true
    }

    fun cancel() {
        val previous = job
        job = null
        previous?.cancel()
    }
}
