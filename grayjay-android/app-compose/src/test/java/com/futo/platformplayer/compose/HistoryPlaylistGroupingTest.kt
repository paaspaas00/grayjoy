package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.*
import com.futo.platformplayer.compose.ui.screens.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HistoryPlaylistGroupingTest {
    @Test fun largeExpandedPlaylistUsesIndexedRowsAndDoesNotRescanHistory() {
        val run = origin("large")
        val videos = List(50_000) { video("v-$it", run) }
        var scans = 0
        val index = indexHistoryPlaylists(videos, videos.mapTo(hashSetOf()) { it.id }) { scans++ }
        val collapsed = index.entries(emptySet())
        assertEquals(1, collapsed.size)
        val scanCount = scans
        val expanded = index.entries(setOf(collapsed[0].key))
        assertEquals(50_001, expanded.size)
        assertSame(videos.last(), (expanded.last() as HistoryListEntry.Video).video)
        assertEquals(scanCount, scans)
        assertEquals(1, index.entries(emptySet()).size)
        assertEquals(scanCount, scans)
    }

    @Test fun playlistNavigationUsesIdentityAndHandlesMissingOrDisabledSources() {
        val local = PlaylistUiModel("list", "Renamed", "", emptyList())
        val source = SourceUiModel("youtube", "YouTube", "", 0, true)
        assertEquals(local, historyOriginPlaylist(origin("r"), listOf(local), emptyList()))
        assertNull(historyOriginPlaylist(origin("r"), listOf(local.copy(id = "other")), emptyList()))
        val remote = origin("r", "https://example.test/playlist", "youtube")
        assertEquals(remote.playlistId, historyOriginPlaylist(remote, emptyList(), listOf(source))!!.id)
        assertNull(historyOriginPlaylist(remote, emptyList(), emptyList()))
        assertNull(historyOriginPlaylist(remote, emptyList(), listOf(source.copy(isEnabled = false))))
        assertNull(historyOriginPlaylist(remote, emptyList(), listOf(source.copy(isLocked = true))))
        assertNull(historyOriginPlaylist(remote, emptyList(), listOf(source.copy(availability = SourceAvailability.MissingPlugin))))
    }
    private fun origin(run: String, id: String = "list", source: String = "") = HistoryPlaylistOrigin(id, source, "Playlist", run)
    private fun video(id: String, origin: HistoryPlaylistOrigin? = null) = VideoUiModel(id, id, "Channel", "", "1:00", lastWatchedAt = 100, historyPlaylistOrigin = origin)
    private fun rows(videos: List<VideoUiModel>, visible: List<VideoUiModel> = videos, expanded: Set<String> = emptySet()) =
        historyPlaylistEntries(videos, visible.mapTo(hashSetOf()) { it.id }, expanded)

    @Test fun separatesNonConsecutiveRunsAndNeverGroupsASingleVideoOrMembershipAlone() {
        val videos = listOf(video("a", origin("new")), video("b", origin("new")), video("plain"),
            video("c", origin("old")), video("d", origin("old")), video("single", origin("single")),
            video("legacy").copy(playlistNames = listOf("Playlist")))
        val result = rows(videos)
        assertEquals(5, result.size)
        assertEquals(listOf("a", "b"), (result[0] as HistoryListEntry.Folder).videos.map { it.id })
        assertEquals(listOf("c", "d"), (result[2] as HistoryListEntry.Folder).videos.map { it.id })
        assertTrue(result[3] is HistoryListEntry.Video)
        assertTrue(result[4] is HistoryListEntry.Video)
    }

    @Test fun filteringDoesNotJoinRunsAcrossAnInvisibleInterruption() {
        val run = origin("one")
        val videos = listOf(video("a", run), video("separator"), video("b", run))
        assertTrue(rows(videos, listOf(videos[0], videos[2])).all { it is HistoryListEntry.Video })
        val mixed = listOf(video("a", run), video("b", run), video("separator"), video("c", run), video("d", run))
        val result = rows(mixed, mixed.filterNot { it.id == "separator" })
        assertEquals(2, result.size)
        assertNotEquals(result[0].key, result[1].key)
    }

    @Test fun expandingKeepsEveryVideoInOrderAndHasStableKeysAsNewVideosArrive() {
        val run = origin("one")
        val videos = listOf(video("b", run), video("c", run))
        val key = rows(videos).single().key
        val newer = listOf(video("a", run)) + videos
        assertEquals(key, rows(newer).single().key)
        val expanded = rows(newer, expanded = setOf(key))
        assertEquals(listOf("a", "b", "c"), expanded.filterIsInstance<HistoryListEntry.Video>().map { it.video.id })
        assertTrue(expanded.filterIsInstance<HistoryListEntry.Video>().all { it.grouped })
        assertEquals(key, rows(newer, listOf(newer[0], newer[1])).first().key)
    }

    @Test fun localAndRemoteIdentitiesAndPlaybackRunsAreDistinct() {
        var ids = 0
        val tracker = HistoryPlaylistRunTracker { "run-${++ids}" }
        val local = PlaylistUiModel("list", "Local", "", emptyList())
        val remote = local.copy(sourceId = "youtube", title = "Remote")
        val a = tracker.record("main", local, 100)
        val b = tracker.record("main", local.copy(title = "Renamed"), 99)
        assertEquals(a.second!!.runId, b.second!!.runId)
        assertTrue(b.first > a.first)
        assertEquals("Renamed", b.second!!.title)
        val c = tracker.record("main", remote, 99)
        assertNotEquals(b.second!!.runId, c.second!!.runId)
        assertNull(tracker.record("main", null, 99).second)
        val d = tracker.record("main", remote, 99)
        assertNotEquals(c.second!!.runId, d.second!!.runId)
        assertNotEquals(d.second!!.runId, tracker.record("other-profile", remote, 99).second!!.runId)
        tracker.reset()
        assertNotEquals(d.second!!.runId, tracker.record("main", remote, 99).second!!.runId)
    }

    @Test fun randomizedGroupingNeverDropsOrReordersVisibleVideos() {
        val random = Random(90241)
        repeat(2000) {
            val videos = (0 until random.nextInt(1, 120)).map { index ->
                val run = random.nextInt(4)
                video("v-$index", if (run == 0) null else origin("run-$run", source = if (run == 3) "youtube" else ""))
            }
            val visible = videos.filter { random.nextBoolean() }
            val collapsed = rows(videos, visible)
            val expanded = rows(videos, visible, collapsed.filterIsInstance<HistoryListEntry.Folder>().mapTo(hashSetOf()) { it.key })
            assertEquals(visible.map { it.id }, expanded.filterIsInstance<HistoryListEntry.Video>().map { it.video.id })
            assertEquals(expanded.size, expanded.map { it.key }.toSet().size)
            collapsed.filterIsInstance<HistoryListEntry.Folder>().forEach { assertTrue(it.videos.size >= 2) }
        }
    }
}
