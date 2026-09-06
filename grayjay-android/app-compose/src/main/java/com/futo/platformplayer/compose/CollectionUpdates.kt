package com.futo.platformplayer.compose

/**
 * Keep unchanged list instances skippable by Compose. Allocate a replacement only after the
 * first changed element; this also avoids copying unrelated feeds for a history checkpoint.
 */
internal inline fun <T> List<T>.mapIfChanged(transform: (T) -> T): List<T> {
    var updated: ArrayList<T>? = null
    for (index in indices) {
        val previous = this[index]
        val next = transform(previous)
        if (updated == null && next !== previous) {
            updated = ArrayList<T>(size).also { it.addAll(subList(0, index)) }
        }
        updated?.add(next)
    }
    return updated ?: this
}
