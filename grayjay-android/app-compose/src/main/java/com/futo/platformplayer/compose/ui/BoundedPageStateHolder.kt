package com.futo.platformplayer.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** Route snapshots otherwise accumulate for every channel ever visited and grow Android's saved Bundle. */
@Composable
internal fun rememberBoundedPageStateHolder(
    maxEntries: Int = 48,
    protectedKeys: Set<String> = emptySet(),
): SaveableStateHolder {
    require(maxEntries >= 2)
    val delegate = rememberSaveableStateHolder()
    val recency = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() }, restore = { it.toMutableStateList() },
    )) { mutableStateListOf<String>() }
    val currentProtection by rememberUpdatedState(protectedKeys)
    val holder = remember(delegate, recency, maxEntries) {
        BoundedPageStateHolder(delegate, recency, maxEntries) { currentProtection }
    }
    SideEffect { holder.trim() }
    return holder
}

/** Keep scroll/filter state for reachable parents and the playlist that minimising playback returns to. */
internal fun protectedNavigationPageKeys(
    profileId: String,
    history: List<BrowseRoute>,
    playingPlaylistId: String?,
): Set<String> = buildSet {
    GrayjayDestination.entries.forEach { add("$profileId:destination:${it.name}") }
    history.takeLast(40).forEach { route ->
        val page = when {
            route.channelId != null -> "channel:${route.channelId}"
            route.playlistId != null -> "playlist:${route.playlistId}"
            else -> "destination:${route.destination}"
        }
        add("$profileId:$page")
    }
    playingPlaylistId?.let { add("$profileId:playlist:$it") }
}

private class BoundedPageStateHolder(
    private val delegate: SaveableStateHolder,
    private val recency: SnapshotStateList<String>,
    private val maxEntries: Int,
    private val protectedKeys: () -> Set<String>,
) : SaveableStateHolder {
    private val active = mutableSetOf<String>()

    @Composable
    override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) {
        require(key is String)
        delegate.SaveableStateProvider(key) {
            DisposableEffect(key) {
                active += key
                recency.remove(key)
                recency += key
                trim()
                onDispose { active -= key; trim() }
            }
            content()
        }
    }

    override fun removeState(key: Any) {
        if (key is String) recency.remove(key)
        delegate.removeState(key)
    }

    fun trim() {
        val retained = protectedKeys()
        while (recency.size > maxEntries || recency.sumOf(String::length) > 16_384) {
            val expired = recency.firstOrNull { it !in active && it !in retained } ?: break
            recency.remove(expired)
            delegate.removeState(expired)
        }
    }
}
