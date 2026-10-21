package com.futo.platformplayer.compose.engine

import java.util.Collections
import java.util.IdentityHashMap

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
