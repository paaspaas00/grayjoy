package com.futo.platformplayer.compose.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap

/** Only routing identity survives process recreation, never loaded video pages or plugin data. */
internal val VisitedChannelsSaver = listSaver<SnapshotStateMap<String, ChannelUiModel>, String>(
    save = { encodeVisitedChannels(it) },
    restore = { values -> mutableStateMapOf<String, ChannelUiModel>().apply { putAll(decodeVisitedChannels(values)) } },
)

internal val VisitedPlaylistsSaver = listSaver<SnapshotStateMap<String, PlaylistUiModel>, String>(
    save = { encodeVisitedPlaylists(it) },
    restore = { values -> mutableStateMapOf<String, PlaylistUiModel>().apply { putAll(decodeVisitedPlaylists(values)) } },
)

internal fun encodeVisitedChannels(channels: Map<String, ChannelUiModel>): List<String> =
    boundedRouteRows(channels.values.asSequence()
        .filter { validRouteIdentity(it.id, it.sourceId) }
        .map { listOf(it.id, it.name.take(160), it.sourceId, it.source.take(160)) })

internal fun decodeVisitedChannels(values: List<String>): Map<String, ChannelUiModel> =
    boundedRouteRows(values.chunked(4).asSequence().filter {
        it.size == 4 && validRouteIdentity(it[0], it[2]) && it[1].length <= 160 && it[3].length <= 160
    }).chunked(4).associate { row ->
        row[0] to ChannelUiModel(row[0], row[1], row[2], row[3], 0, "", "")
    }

internal fun encodeVisitedPlaylists(playlists: Map<String, PlaylistUiModel>): List<String> =
    boundedRouteRows(playlists.values.asSequence()
        .filter { it.sourceId.isNotBlank() && validRouteIdentity(it.id, it.sourceId) }
        .map { listOf(it.id, it.title.take(160), it.sourceId) })

internal fun decodeVisitedPlaylists(values: List<String>): Map<String, PlaylistUiModel> =
    boundedRouteRows(values.chunked(3).asSequence().filter {
        it.size == 3 && it[2].isNotBlank() && validRouteIdentity(it[0], it[2]) && it[1].length <= 160
    }).chunked(3).associate { row ->
        row[0] to PlaylistUiModel(row[0], row[1], "", emptyList(), sourceId = row[2])
    }

private fun validRouteIdentity(id: String, sourceId: String): Boolean =
    id.isNotBlank() && id.length <= 4_096 && sourceId.length <= 512

private fun boundedRouteRows(rows: Sequence<List<String>>): List<String> = buildList {
    var remainingCharacters = 16_384
    var entries = 0
    for (row in rows) {
        if (entries >= 42) break
        val characters = row.sumOf(String::length)
        if (characters > remainingCharacters) continue
        addAll(row)
        remainingCharacters -= characters
        entries++
    }
}
