package com.futo.platformplayer.compose.engine

import java.util.Collections
import java.util.IdentityHashMap

/** Fallback engines retain the secondary failure as suppressed, not as the primary cause. */
internal fun Throwable.boundedFailures(limit: Int = 32): Sequence<Throwable> = sequence {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val pending = java.util.ArrayDeque<Throwable>().apply { add(this@boundedFailures) }
    val maximum = limit.coerceAtLeast(1)
    while (pending.isNotEmpty() && seen.size < maximum) {
        val next = pending.removeFirst()
        if (!seen.add(next)) continue
        yield(next)
        runCatching { next.cause }.getOrNull()?.let(pending::add)
        next.suppressed.take(4).forEach(pending::add)
    }
}

internal fun Throwable.isPendingLiveFailure(): Boolean = boundedFailures().any {
    val message = runCatching { it.message }.getOrNull().orEmpty().take(4096).lowercase(java.util.Locale.ROOT)
    listOf("live_stream_offline", "will begin in", "live event will begin", "premiere will begin", "not yet started").any(message::contains)
}

/** A plugin exception can contain cycles or thousands of wrappers; error handling must terminate. */
internal fun Throwable.boundedCauses(limit: Int = 32): Sequence<Throwable> = sequence {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this@boundedCauses
    var remaining = limit.coerceAtLeast(1)
    while (remaining-- > 0) {
        val cause = current ?: break
        if (!visited.add(cause)) break
        yield(cause)
        current = cause.cause
    }
}
