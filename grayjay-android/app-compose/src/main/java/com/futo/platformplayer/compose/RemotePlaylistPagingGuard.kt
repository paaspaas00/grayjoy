package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.RemotePlaylistDetailUiState
import java.io.IOException

internal class RemotePlaylistPaginationException(message: String) : IOException(message)

/** Stateful pagers may reuse their cursor; actual item growth, not token equality, is progress. */
internal class RemotePlaylistPagingGuard(
    private val maxStagnantPages: Int = 8,
    private val maxPages: Int = 1_000,
) {
    private var stagnantPages = 0
    private var pages = 0
    var hasFailed: Boolean = false
        private set

    fun recordPage(previousCount: Int, currentCount: Int, hasMore: Boolean, continuationId: String?) {
        pages++
        stagnantPages = if (currentCount > previousCount) 0 else stagnantPages + 1
        if (!hasMore) return
        if (continuationId.isNullOrBlank()) fail("Playlist pagination returned no continuation.")
        if (stagnantPages >= maxStagnantPages || pages >= maxPages) {
            fail("Playlist pagination stopped making progress. Reopen the playlist to retry.")
        }
    }

    private fun fail(message: String): Nothing {
        hasFailed = true
        throw RemotePlaylistPaginationException(message)
    }
}

internal fun remotePlaylistRequestMatches(
    detail: RemotePlaylistDetailUiState,
    playlistId: String,
    generation: Long,
    currentGeneration: Long,
): Boolean = generation == currentGeneration && detail.playlist?.id == playlistId
