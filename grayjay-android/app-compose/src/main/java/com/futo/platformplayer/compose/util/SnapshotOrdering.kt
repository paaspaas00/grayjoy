package com.futo.platformplayer.compose.util

/** Comparators must not read timestamps/state that concurrent readers or writers can change. */
internal fun <T> snapshotSortedBy(items: Iterable<T>, key: (T) -> Long): List<T> =
    items.map { it to key(it) }.sortedBy { it.second }.map { it.first }
