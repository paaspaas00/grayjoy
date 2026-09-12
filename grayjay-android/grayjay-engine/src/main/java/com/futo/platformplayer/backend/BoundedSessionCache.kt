package com.futo.platformplayer.backend

/** In-memory handles to extractors must not retain every page visited since app startup. */
internal class BoundedSessionCache<K, V>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val items = LinkedHashMap<K, V>(16, 0.75f, true)

    @Synchronized operator fun get(key: K): V? = items[key]

    @Synchronized operator fun set(key: K, value: V) {
        items[key] = value
        while (items.size > capacity) {
            val iterator = items.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    @Synchronized fun remove(key: K): V? = items.remove(key)
    @Synchronized fun remove(key: K, value: V): Boolean =
        if (items[key] === value) { items.remove(key); true } else false
    @Synchronized fun clear() = items.clear()
    val size: Int @Synchronized get() = items.size
    val keys: Set<K> @Synchronized get() = items.keys.toSet()
}
