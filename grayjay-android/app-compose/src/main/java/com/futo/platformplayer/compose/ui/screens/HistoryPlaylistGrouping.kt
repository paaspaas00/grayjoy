package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.HistoryPlaylistOrigin
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.SourceAvailability

internal fun historyOriginPlaylist(origin: HistoryPlaylistOrigin, playlists: List<PlaylistUiModel>, sources: List<SourceUiModel>): PlaylistUiModel? {
    if (origin.playlistId.isBlank()) return null
    if (origin.sourceId.isBlank()) return playlists.firstOrNull { it.id == origin.playlistId && it.sourceId.isBlank() }
    if (sources.none { it.id == origin.sourceId && it.isEnabled && !it.isLocked && it.availability != SourceAvailability.MissingPlugin }) return null
    return PlaylistUiModel(origin.playlistId, origin.title, "", emptyList(), sourceId = origin.sourceId)
}

internal sealed interface HistoryListEntry {
    val key: String
    data class Video(val video: VideoUiModel, val grouped: Boolean = false, val parentKey: String = "") : HistoryListEntry {
        override val key: String get() = if (parentKey.isEmpty()) video.id else "$parentKey:video:${video.id}"
    }
    data class Folder(val origin: HistoryPlaylistOrigin, val videos: List<VideoUiModel>, val expanded: Boolean,
        override val key: String, val collectionGrouping: LibraryGrouping? = null) : HistoryListEntry
}

/** One metadata scan per history/filter change; no per-video rows are allocated on expand/collapse. */
internal class HistoryPlaylistIndex internal constructor(private val sections: List<Section>) {
    internal data class Section(val videos: List<VideoUiModel>, val origin: HistoryPlaylistOrigin?, val key: String,
        val collectionGrouping: LibraryGrouping? = null)

    fun entries(expandedGroups: Set<String>): List<HistoryListEntry> {
        val ends = IntArray(sections.size)
        var count = 0
        sections.forEachIndexed { index, section ->
            count += if (section.origin == null) section.videos.size
                else 1 + if (section.key in expandedGroups) section.videos.size else 0
            ends[index] = count
        }
        return object : AbstractList<HistoryListEntry>() {
            override val size = count
            override fun get(index: Int): HistoryListEntry {
                checkElementIndex(index, size)
                val found = ends.binarySearch(index + 1)
                val sectionIndex = if (found >= 0) found else -found - 1
                val section = sections[sectionIndex]
                val position = index - if (sectionIndex == 0) 0 else ends[sectionIndex - 1]
                return if (section.origin != null && position == 0)
                    HistoryListEntry.Folder(section.origin, section.videos, section.key in expandedGroups, section.key, section.collectionGrouping)
                else HistoryListEntry.Video(section.videos[position - if (section.origin == null) 0 else 1], section.origin != null,
                    if (section.collectionGrouping != null) section.key else "")
            }
        }
    }
}

private fun checkElementIndex(index: Int, size: Int) {
    if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index, size=$size")
}

internal fun historyVideoEntries(videos: List<VideoUiModel>): List<HistoryListEntry> = object : AbstractList<HistoryListEntry>() {
    override val size get() = videos.size
    override fun get(index: Int): HistoryListEntry = HistoryListEntry.Video(videos[index])
}

/** Boundaries precede filtering: hidden intervening videos must not merge separate listening runs. */
internal fun indexHistoryPlaylists(
    orderedHistory: List<VideoUiModel>,
    visibleIds: Set<String>,
    checkpoint: () -> Unit = {},
): HistoryPlaylistIndex {
    val sections = ArrayList<HistoryPlaylistIndex.Section>()
    var singles = ArrayList<VideoUiModel>()
    fun flushSingles() {
        if (singles.isNotEmpty()) sections.add(HistoryPlaylistIndex.Section(singles, null, ""))
        singles = ArrayList()
    }
    var index = 0
    while (index < orderedHistory.size) {
        checkpoint()
        val start = index
        val origin = orderedHistory[index].historyPlaylistOrigin?.takeIf { it.playlistId.isNotBlank() && it.runId.isNotBlank() }
        index++
        if (origin == null) {
            orderedHistory[start].takeIf { it.id in visibleIds }?.let(singles::add)
            continue
        }
        while (index < orderedHistory.size) {
            if (index % 256 == 0) checkpoint()
            val other = orderedHistory[index].historyPlaylistOrigin ?: break
            if (other.playlistId != origin.playlistId || other.sourceId != origin.sourceId || other.runId != origin.runId) break
            index++
        }
        val visible = orderedHistory.subList(start, index).filter { it.id in visibleIds }
        if (visible.size > 1) {
            val key = "history-group:${origin.runId}:${orderedHistory[index - 1].id}"
            flushSingles()
            sections.add(HistoryPlaylistIndex.Section(visible, origin, key))
        } else singles.addAll(visible)
    }
    flushSingles()
    return HistoryPlaylistIndex(sections)
}

internal fun historyPlaylistEntries(orderedHistory: List<VideoUiModel>, visibleIds: Set<String>, expandedGroups: Set<String>) =
    indexHistoryPlaylists(orderedHistory, visibleIds).entries(expandedGroups)
