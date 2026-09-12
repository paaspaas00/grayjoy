package com.futo.platformplayer.compose.casting

import java.io.IOException
import java.net.URI
import java.util.UUID

/** Keeps repeated HLS reloads stable, and prevents old responses repopulating a new session. */
internal class CastRouteRegistry<T>(private val maxEntries: Int = 8192) {
    init { require(maxEntries > 0) }
    data class Entry<T>(val route: T, val generation: Long)
    private val byPath = LinkedHashMap<String, Entry<T>>(16, 0.75f, true)
    private val paths = HashMap<T, String>()
    @Volatile var generation: Long = 0L
        private set

    @Synchronized fun reset(): Long {
        byPath.clear()
        paths.clear()
        return ++generation
    }

    @Synchronized fun add(route: T, expectedGeneration: Long, prefix: String, suffix: String = ""): String {
        if (expectedGeneration != generation) throw IOException("Cast session has ended")
        paths[route]?.let { path -> byPath[path]; return path }
        val path = "/$prefix-${UUID.randomUUID()}$suffix"
        paths[route] = path
        byPath[path] = Entry(route, generation)
        while (byPath.size > maxEntries) {
            val iterator = byPath.entries.iterator()
            paths.remove(iterator.next().value.route)
            iterator.remove()
        }
        return path
    }

    @Synchronized operator fun get(path: String): Entry<T>? = byPath[path]
    val size: Int @Synchronized get() = byPath.size
}

internal fun rewriteHlsPlaylist(
    content: String,
    baseUrl: String,
    routeUrl: (upstream: String, playlist: Boolean) -> String,
): String {
    var nextIsPlaylist = false
    val base = URI(baseUrl)
    fun rewrite(reference: String, playlist: Boolean): String {
        val uri = base.resolve(reference)
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) {
            throw IOException("Unsupported HLS resource scheme")
        }
        return routeUrl(uri.toString(), playlist || uri.path.orEmpty().endsWith(".m3u8", true))
    }
    return content.lineSequence().joinToString("\n") { original ->
        val line = original.trim()
        when {
            line.startsWith("#EXT-X-STREAM-INF:") -> { nextIsPlaylist = true; original }
            line.startsWith("#") -> {
                val playlist = line.startsWith("#EXT-X-MEDIA:") ||
                    line.startsWith("#EXT-X-I-FRAME-STREAM-INF:") ||
                    line.startsWith("#EXT-X-RENDITION-REPORT:")
                HLS_URI_ATTRIBUTE.replace(original) { match ->
                    "URI=\"${rewrite(match.groupValues[1], playlist)}\""
                }
            }
            line.isBlank() -> original
            else -> rewrite(line, nextIsPlaylist).also { nextIsPlaylist = false }
        }
    }
}

private val HLS_URI_ATTRIBUTE = Regex("URI=\"([^\"]+)\"")

internal data class CastByteRange(val start: Long = 0L, val length: Long? = null, val suffix: Long? = null)

internal fun parseCastByteRange(value: String): CastByteRange? {
    val match = BYTE_RANGE.matchEntire(value.trim()) ?: return null
    val first = match.groupValues[1]
    val last = match.groupValues[2]
    if (first.isEmpty()) {
        val suffix = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return CastByteRange(suffix = suffix)
    }
    val start = first.toLongOrNull() ?: return null
    if (last.isEmpty()) return CastByteRange(start)
    val end = last.toLongOrNull() ?: return null
    if (end < start || end - start == Long.MAX_VALUE) return null
    return CastByteRange(start, end - start + 1)
}

private val BYTE_RANGE = Regex("bytes=(\\d*)-(\\d*)", RegexOption.IGNORE_CASE)

/** A receiver must never see a range end of '*' or an end inconsistent with the body length. */
internal fun castContentRange(range: CastByteRange?, length: Long, upstream: String?): String? {
    val match = upstream?.let { CONTENT_RANGE.matchEntire(it.trim()) }
    val upstreamStart = match?.groupValues?.get(1)?.toLongOrNull()
    val upstreamEnd = match?.groupValues?.get(2)?.toLongOrNull()
    val totalText = match?.groupValues?.get(3)
    val total = totalText?.toLongOrNull()
    val validUpstream = upstreamStart != null && upstreamEnd != null &&
        upstreamEnd >= upstreamStart && (totalText == "*" || (total != null && total > upstreamEnd))
    val start = if (range?.suffix != null || range == null) {
        upstreamStart?.takeIf { validUpstream } ?: return null
    } else range.start
    val end = if (length > 0) {
        if (length - 1 > Long.MAX_VALUE - start) return null
        start + length - 1
    } else {
        upstreamEnd?.takeIf { validUpstream && upstreamStart == start && length < 0 } ?: return null
    }
    if (validUpstream && total != null && end >= total) return null
    return "bytes $start-$end/${if (validUpstream) totalText else "*"}"
}

private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
