package com.futo.platformplayer.compose.shorts

import java.net.URI

internal const val SHORTS_RESOLVED_TTL_MS = 3 * 60_000L

/** Current, next, previous: bounded and independent of how large the feed becomes. */
internal fun shortsPrefetchWindow(ids: List<String>, index: Int): List<String> =
    listOfNotNull(ids.getOrNull(index), ids.getOrNull(index + 1), ids.getOrNull(index - 1)).distinct()

internal fun shortsDescriptorTtlMs(urls: List<String>, wallClockMs: Long): Long {
    val expires = urls.mapNotNull { raw ->
        runCatching { URI(raw).rawQuery }.getOrNull()?.split('&')?.firstNotNullOfOrNull { parameter ->
            if (parameter.substringBefore('=') != "expire") null
            else parameter.substringAfter('=', "").toLongOrNull()?.takeIf { it in 1..Long.MAX_VALUE / 1000 }
        }
    }.minOrNull()
    return if (expires == null) SHORTS_RESOLVED_TTL_MS
    else (expires * 1000L - wallClockMs - 30_000L).coerceIn(0L, SHORTS_RESOLVED_TTL_MS)
}

/** Monotonic TTL and LRU; never retain descriptors from an obsolete/profile-switched session. */
internal class ShortsResolvedCache<T>(private val capacity: Int = 5) {
    private data class Entry<T>(val value: T, val expiresAtMs: Long)
    private val entries = LinkedHashMap<String, Entry<T>>(8, 0.75f, true)
    var generation: Long = 0L
        private set
    private var allowed = emptySet<String>()

    fun setWindow(ids: List<String>) { allowed = ids.toSet() }
    fun accepts(id: String, expectedGeneration: Long): Boolean =
        expectedGeneration == generation && id in allowed

    fun get(id: String, nowMs: Long): T? {
        val entry = entries[id] ?: return null
        if (entry.expiresAtMs <= nowMs) { entries.remove(id); return null }
        return entry.value
    }

    fun put(id: String, value: T, nowMs: Long, ttlMs: Long, expectedGeneration: Long): Boolean {
        if (!accepts(id, expectedGeneration) || ttlMs <= 0L) return false
        entries[id] = Entry(value, nowMs + minOf(ttlMs, Long.MAX_VALUE - nowMs))
        while (entries.size > capacity.coerceAtLeast(1)) entries.remove(entries.keys.first())
        return true
    }

    fun clear() { generation++; allowed = emptySet(); entries.clear() }
    internal fun ids(): Set<String> = entries.keys.toSet()
    fun values(nowMs: Long): List<T> {
        entries.entries.removeAll { it.value.expiresAtMs <= nowMs }
        return entries.values.map { it.value }
    }
}

/** Only explicitly prefetched prefixes are retained; ordinary playback never fills this cache. */
internal class BoundedMediaPrefixCache(private val maxBytes: Int = 3 * 1024 * 1024) {
    data class Prefix(val bytes: ByteArray, val expiresAtMs: Long, val totalLength: Long?)
    private val entries = LinkedHashMap<String, Prefix>(8, 0.75f, true)
    private var sizeBytes = 0
    private var generation = 0L

    @Synchronized fun generation(): Long = generation
    @Synchronized fun get(key: String, nowMs: Long): Prefix? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMs <= nowMs) { entries.remove(key); sizeBytes -= entry.bytes.size; return null }
        return entry
    }

    @Synchronized fun put(key: String, prefix: Prefix, expectedGeneration: Long): Boolean {
        if (generation != expectedGeneration || prefix.bytes.isEmpty() || prefix.bytes.size > maxBytes) return false
        entries.remove(key)?.let { sizeBytes -= it.bytes.size }
        entries[key] = prefix
        sizeBytes += prefix.bytes.size
        while (sizeBytes > maxBytes) entries.remove(entries.keys.first())?.let { sizeBytes -= it.bytes.size }
        return true
    }

    @Synchronized fun clear() { generation++; entries.clear(); sizeBytes = 0 }
    @Synchronized internal fun byteCount(): Int = sizeBytes
}
