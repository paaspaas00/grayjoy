package com.futo.platformplayer.compose.downloads

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Unknown or temporarily regressing encoder progress must not move the job bar backwards. */
internal class MediaExportItemProgressTracker {
    private val knownProgress = mutableMapOf<MediaExportStage, Float>()

    fun update(stage: MediaExportStage, value: Float?): Float? {
        val previous = knownProgress[stage]
        val current = value?.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: return previous
        return maxOf(previous ?: 0f, current).also { knownProgress[stage] = it }
    }
}

/** The monitor shares the export scope, so a full disk cancels and awaits its writer cleanup. */
internal suspend fun <T> withExportStorageGuard(
    checkSpace: suspend () -> Unit,
    intervalMs: Long = 500L,
    action: suspend () -> T,
): T = coroutineScope {
    checkSpace()
    val monitor = launch {
        while (true) {
            delay(intervalMs.coerceAtLeast(1L))
            checkSpace()
        }
    }
    try {
        action()
    } finally {
        monitor.cancelAndJoin()
    }
}

/** Keep names below common provider/filesystem byte limits without splitting surrogate pairs. */
internal fun mediaExportFileBaseName(title: String): String {
    val sanitized = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cc}]"), "_").trim(' ', '.')
    return buildString {
        var offset = 0
        var utf8Bytes = 0
        while (offset < sanitized.length) {
            val codePoint = sanitized.codePointAt(offset)
            val safeCodePoint = if (codePoint in 0xD800..0xDFFF) '_'.code else codePoint
            val character = String(Character.toChars(safeCodePoint))
            val bytes = character.toByteArray(Charsets.UTF_8).size
            if (length + character.length > 120 || utf8Bytes + bytes > 200) break
            append(character)
            utf8Bytes += bytes
            offset += Character.charCount(codePoint)
        }
    }.trimEnd(' ', '.').ifBlank { "Grayjoy download" }
}
