package com.futo.platformplayer.compose.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.CancellationException

/** Outgoing pages and pages covered by the player must never consume system back. */
internal val LocalPageBackEnabled = staticCompositionLocalOf { true }

@Composable
internal fun PageBackHandler(enabled: Boolean, onBack: () -> Unit) {
    PredictiveBackHandler(enabled = enabled && LocalPageBackEnabled.current) { events ->
        try {
            events.collect { /* Selection/focus changes are committed only after the gesture. */ }
            onBack()
        } catch (_: CancellationException) {
            // A cancelled gesture leaves focus, selection and the current subview untouched.
        }
    }
}

internal data class BrowseRoute(
    val destination: String,
    val channelId: String? = null,
    val playlistId: String? = null,
    val libraryFilter: String = "History",
    val videoId: String? = null,
    val parentDestination: String? = null,
)

internal fun appendBrowseRoute(history: List<BrowseRoute>, route: BrowseRoute): List<BrowseRoute> =
    if (history.lastOrNull() == route) history else (history + route).takeLast(40)

internal fun browseHistoryForShortcut(
    history: List<BrowseRoute>,
    current: BrowseRoute,
    target: BrowseRoute,
): List<BrowseRoute> = if (current == target) history else appendBrowseRoute(history, current)

internal fun shouldRestorePlaylistWhenClosingPlayer(expandedVideoId: String?, fullscreen: Boolean): Boolean =
    expandedVideoId != null || fullscreen

internal fun initialPlayerTransitionProgress(expandedVideoId: String?): Float =
    if (expandedVideoId == null) 1f else 0f

internal fun shouldRestorePlaylistOnPlayerSettle(previousTarget: Float, nextTarget: Float): Boolean =
    previousTarget < 0.5f && nextTarget >= 0.5f

internal fun historyForRemotePlaybackPlaylistReturn(
    history: List<BrowseRoute>,
    current: BrowseRoute,
    playlistId: String,
): List<BrowseRoute> = if (current.playlistId == playlistId) history
    else appendBrowseRoute(history, current.copy(videoId = null))

internal fun encodeBrowseHistory(history: List<BrowseRoute>): List<String> =
    listOf("routes-v2") + history.takeLast(40).flatMap {
        listOf(it.destination, it.channelId.orEmpty(), it.playlistId.orEmpty(), it.libraryFilter,
            it.videoId.orEmpty(), it.parentDestination.orEmpty())
    }

internal fun decodeBrowseHistory(values: List<String>): List<BrowseRoute> {
    val versioned = values.firstOrNull() == "routes-v2"
    val fields = if (versioned) 6 else 5
    return (if (versioned) values.drop(1) else values).chunked(fields)
        .filter { it.size == fields }
        .map {
            BrowseRoute(it[0], it[1].ifEmpty { null }, it[2].ifEmpty { null }, it[3],
                it[4].ifEmpty { null }, it.getOrNull(5)?.ifEmpty { null })
        }.takeLast(40)
}

internal val BrowseHistorySaver = listSaver<List<BrowseRoute>, String>(
    save = { encodeBrowseHistory(it) },
    restore = { decodeBrowseHistory(it) },
)
