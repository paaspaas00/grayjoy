package com.futo.platformplayer.compose.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Each source advances independently. Keep successful pages until the complete batch is delivered,
 * so a sibling failure or a cancelled UI request cannot consume results the user has never seen.
 */
internal class RetainedPageSequence<T>(
    parent: CoroutineScope,
    ids: List<String>,
    private val load: suspend (String) -> T,
    private val continuation: (T) -> String?,
) : AutoCloseable {
    private class Branch<T>(val id: String, var pending: Deferred<T>? = null)
    private val lifetime = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + lifetime)
    private val mutex = Mutex()
    private var branches = ids.distinct().map { Branch<T>(it) }
    val hasMore: Boolean get() = branches.isNotEmpty()
    private fun ensureOpen() {
        check(lifetime.isActive) { "These results have expired. Refresh the list to continue." }
    }

    suspend fun next(): List<T> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        ensureOpen()
        branches.forEach { branch ->
            if (branch.pending == null) branch.pending = scope.async { load(branch.id) }
        }
        val pages = branches.map { branch ->
            try {
                requireNotNull(branch.pending).await()
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                ensureOpen()
                branch.pending = null
                throw cancelled
            } catch (failure: Exception) {
                branch.pending = null
                throw failure
            }
        }
        currentCoroutineContext().ensureActive()
        ensureOpen()
        // Preserve source identity even when a different source has reached its final page.
        branches = pages.mapNotNull(continuation).distinct().map { Branch<T>(it) }
        pages
    }

    override fun close() { lifetime.cancel() }
}

internal class BoundedSessionRegistry<T : AutoCloseable>(private val limit: Int) {
    init { require(limit > 0) }
    private val sessions = LinkedHashMap<String, T>(limit, 0.75f, true)
    @Synchronized operator fun get(id: String): T? = sessions[id]
    @Synchronized operator fun set(id: String, value: T) {
        sessions.put(id, value)?.takeIf { it !== value }?.close()
        while (sessions.size > limit) {
            val first = sessions.entries.iterator()
            val old = first.next().value
            first.remove()
            old.close()
        }
    }
    @Synchronized fun remove(id: String) { sessions.remove(id)?.close() }
    @Synchronized fun clear() { sessions.values.forEach { it.close() }; sessions.clear() }
}
