package com.futo.platformplayer.compose

/** Keep complete list snapshots when following nested links, with a bounded navigation lifetime. */
internal class NavigationContentCache<T>(
    private val maxEntries: Int = 6,
    private val retentionMs: Long = 15L * 60L * 1000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Entry<T>(val value: T, val savedAt: Long)
    private val entries = LinkedHashMap<String, Entry<T>>(maxEntries.coerceAtLeast(1), 0.75f, true)

    fun put(key: String, value: T) {
        if (key.isBlank() || maxEntries <= 0) return
        entries[key] = Entry(value, clock())
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    fun get(key: String): T? {
        val entry = entries[key] ?: return null
        if (clock() - entry.savedAt !in 0..retentionMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun clear() = entries.clear()
}
