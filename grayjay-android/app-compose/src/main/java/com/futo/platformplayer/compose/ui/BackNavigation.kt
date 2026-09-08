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
)

internal fun appendBrowseRoute(history: List<BrowseRoute>, route: BrowseRoute): List<BrowseRoute> =
    if (history.lastOrNull() == route) history else (history + route).takeLast(40)

internal val BrowseHistorySaver = listSaver<List<BrowseRoute>, String>(
    save = { history -> history.flatMap { listOf(it.destination, it.channelId.orEmpty(), it.playlistId.orEmpty(), it.libraryFilter, it.videoId.orEmpty()) } },
    restore = { values -> values.chunked(5).filter { it.size == 5 }.map {
        BrowseRoute(it[0], it[1].ifEmpty { null }, it[2].ifEmpty { null }, it[3], it[4].ifEmpty { null })
    } },
)
