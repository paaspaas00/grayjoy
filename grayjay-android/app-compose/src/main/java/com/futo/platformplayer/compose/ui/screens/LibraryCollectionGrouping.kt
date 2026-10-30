package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.HistoryPlaylistOrigin
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

internal enum class LibraryGrouping(val labelRes: Int) {
    None(R.string.library_group_none), Playlist(R.string.library_group_playlist), Channel(R.string.library_group_channel),
}

/** Built off the UI thread. Expansion uses the same lazy row index as the history. */
internal fun indexLibraryCollection(videos: List<VideoUiModel>, playlists: List<PlaylistUiModel>, grouping: LibraryGrouping,
    checkpoint: () -> Unit = {}): HistoryPlaylistIndex {
    if (grouping == LibraryGrouping.None) return HistoryPlaylistIndex(listOf(HistoryPlaylistIndex.Section(videos, null, "")))
    val origins = linkedMapOf<String, HistoryPlaylistOrigin>()
    val members = linkedMapOf<String, MutableList<VideoUiModel>>()
    val memberships = HashMap<String, MutableList<String>>()
    if (grouping == LibraryGrouping.Playlist) for (playlist in playlists) {
        checkpoint()
        if (playlist.isPlaybackQueue) continue
        val key = "collection-playlist:${playlist.sourceId.length}:${playlist.sourceId}:${playlist.id}"
        origins[key] = HistoryPlaylistOrigin(playlist.id, playlist.sourceId, playlist.title, "")
        for (id in playlist.videoIds.distinct()) memberships.getOrPut(id) { arrayListOf() }.add(key)
    }
    val singles = ArrayList<VideoUiModel>()
    for (video in videos.distinctBy(VideoUiModel::id)) {
        checkpoint()
        val keys = if (grouping == LibraryGrouping.Channel) {
            val channel = video.authorUrl.ifBlank { video.channelId.ifBlank { video.creator } }
            if (channel.isBlank()) emptyList() else {
                val key = "collection-channel:${video.sourceId.length}:${video.sourceId}:$channel"
                origins[key] = HistoryPlaylistOrigin(channel, video.sourceId, video.creator, "")
                listOf(key)
            }
        } else memberships[video.id].orEmpty().ifEmpty {
            // A remote playlist may no longer be loaded; retain its recorded playback origin.
            video.historyPlaylistOrigin?.takeIf { it.playlistId.isNotBlank() }?.let { origin ->
                val key = "collection-playlist:${origin.sourceId.length}:${origin.sourceId}:${origin.playlistId}"
                origins[key] = origin
                listOf(key)
            }.orEmpty()
        }
        if (keys.isEmpty()) singles.add(video)
        else for (key in keys) members.getOrPut(key) { arrayListOf() }.add(video)
    }
    val sections = members.map { (key, items) -> HistoryPlaylistIndex.Section(items, origins.getValue(key), key, grouping) }.toMutableList()
    if (singles.isNotEmpty()) sections.add(HistoryPlaylistIndex.Section(singles, null, ""))
    return HistoryPlaylistIndex(sections)
}
