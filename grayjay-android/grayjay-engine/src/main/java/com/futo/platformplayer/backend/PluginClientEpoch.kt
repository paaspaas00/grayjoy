package com.futo.platformplayer.backend

internal class SourceSessionChangedException :
    IllegalStateException("Source session changed during loading; retry the operation")

/** Short critical sections only; network and V8 initialization must run outside this lock. */
internal class PluginClientEpoch {
    private var generation = 0L

    @Synchronized fun snapshot(): Long = generation

    @Synchronized fun <T> current(expected: Long, action: () -> T): T {
        // Invalidating a client is not coroutine cancellation. Callers must still execute their
        // normal failure/cleanup path when another operation changes source settings.
        if (generation != expected) throw SourceSessionChangedException()
        return action()
    }

    @Synchronized fun <T> invalidate(action: () -> T): T {
        generation++
        return action()
    }
}
