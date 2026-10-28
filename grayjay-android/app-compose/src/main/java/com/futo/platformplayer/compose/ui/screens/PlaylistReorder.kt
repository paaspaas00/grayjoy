package com.futo.platformplayer.compose.ui.screens

/** Keep the playing item first when entering shuffled order; never drop or duplicate tracks. */
internal fun shuffledReorderIds(ids: List<String>, currentId: String?, random: kotlin.random.Random = kotlin.random.Random.Default): List<String> {
    val unique = ids.distinct()
    val first = currentId?.takeIf(unique::contains)
    return listOfNotNull(first) + unique.filterNot { it == first }.shuffled(random)
}

internal fun reconcileReorderDraft(draft: List<String>, current: List<String>): List<String> {
    val currentIds = current.toSet()
    val retained = draft.filter { it in currentIds }.distinct()
    return retained + current.filterNot(retained.toSet()::contains).distinct()
}

internal fun moveReorderItem(ids: List<String>, id: String, target: String): List<String> {
    val from = ids.indexOf(id)
    val to = ids.indexOf(target)
    if (from < 0 || to < 0 || from == to) return ids
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun commitReorderSlot(ids: List<String>, draggedId: String, slot: Int): List<String> {
    if (draggedId !in ids) return ids
    val remaining = ids.filterNot { it == draggedId }.toMutableList()
    remaining.add(slot.coerceIn(0, remaining.size), draggedId)
    return remaining
}

/** Require the pointer to cross a neighbouring row's midpoint; slot bounds are not a target. */
internal fun reorderSlotDelta(center: Float, previousMidpoint: Float?, nextMidpoint: Float?, hysteresis: Float): Int = when {
    previousMidpoint != null && center < previousMidpoint - hysteresis -> -1
    nextMidpoint != null && center > nextMidpoint + hysteresis -> 1
    else -> 0
}
