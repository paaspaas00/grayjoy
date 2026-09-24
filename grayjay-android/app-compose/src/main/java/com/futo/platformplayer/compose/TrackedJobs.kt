package com.futo.platformplayer.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Shared prerequisites report failure to each consumer without cancelling their parent job. */
internal fun <T> CoroutineScope.asyncJobResult(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): Deferred<Result<T>> = async(context) {
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

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
