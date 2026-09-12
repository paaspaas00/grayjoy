package com.futo.platformplayer.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Registry and scope must share an owner dispatcher (the UI dispatcher in the ViewModel). */
internal fun <K> CoroutineScope.launchTracked(
    registry: MutableMap<K, Job>,
    key: K,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val next = launch(start = CoroutineStart.LAZY) {
        try {
            block()
        } finally {
            if (registry[key] === coroutineContext[Job]) registry.remove(key)
        }
    }
    registry.put(key, next)?.cancel()
    // A scope already cancelled cannot enter the body/finally.
    if (!next.start() && registry[key] === next) registry.remove(key)
    return next
}
