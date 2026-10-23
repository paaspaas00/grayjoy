package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.HistoryPlaylistOrigin
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import java.util.UUID

internal data class RecordedHistoryPlayback(val profileId: String, val videoId: String,
    val watchedAt: Long, val origin: HistoryPlaylistOrigin?)

/** Capture on the playback thread before asynchronous storage can reorder completions. */
internal class HistoryPlaylistRunTracker(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private var identity: Triple<String, String, String>? = null
    private var runId: String? = null
    private var lastTimestamp = 0L
    fun reset() { identity = null; runId = null }
    fun record(profileId: String, playlist: PlaylistUiModel?, now: Long): Pair<Long, HistoryPlaylistOrigin?> {
        lastTimestamp = maxOf(now, lastTimestamp + 1L)
        if (playlist == null || playlist.id.isBlank()) { reset(); return lastTimestamp to null }
        val next = Triple(profileId, playlist.sourceId, playlist.id)
        if (next != identity) { identity = next; runId = newId() }
        return lastTimestamp to HistoryPlaylistOrigin(playlist.id, playlist.sourceId, playlist.title, checkNotNull(runId))
    }
}
