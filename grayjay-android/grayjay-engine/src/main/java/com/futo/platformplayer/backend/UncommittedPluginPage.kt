package com.futo.platformplayer.backend

import com.futo.platformplayer.api.media.structures.IPager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class UncommittedPluginPage(
    val items: List<Any>,
    val keys: Set<String>,
    val hasMore: Boolean,
)

/** Reading may advance a plugin's page, but keys are acknowledged only after the whole batch succeeds. */
internal suspend fun readUncommittedPluginPage(
    pager: IPager<*>,
    emittedKeys: Set<String>,
    pageSize: Int,
    keyOf: (Any) -> String,
): UncommittedPluginPage {
    currentCoroutineContext().ensureActive()
    var results = pager.getResults().filterNotNull()
    fun unread(): List<Any> = results.asSequence().filter { keyOf(it) !in emittedKeys }
        .distinctBy(keyOf).take(pageSize.coerceAtLeast(1)).toList()
    var items = unread()
    if (items.isEmpty() && pager.hasMorePages()) {
        pager.nextPage()
        results = pager.getResults().filterNotNull()
        items = unread()
    }
    val keys = items.mapTo(linkedSetOf(), keyOf)
    val hasMore = results.any { keyOf(it) !in emittedKeys && keyOf(it) !in keys } || pager.hasMorePages()
    currentCoroutineContext().ensureActive()
    return UncommittedPluginPage(items, keys, hasMore)
}
