package com.futo.platformplayer.compose.engine

/** Late-resolved items must follow the logical queue, not resolution completion order. */
internal fun queueInsertionIndex(currentIds: List<String>, orderedIds: List<String>, newId: String): Int {
    val positions = orderedIds.withIndex().associate { it.value to it.index }
    val desiredIndex = positions[newId] ?: return currentIds.size
    return currentIds.indexOfFirst { (positions[it] ?: -1) > desiredIndex }
        .takeIf { it >= 0 } ?: currentIds.size
}
