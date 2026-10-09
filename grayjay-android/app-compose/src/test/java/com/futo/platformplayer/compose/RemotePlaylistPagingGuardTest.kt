package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.RemotePlaylistDetailUiState
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class RemotePlaylistPagingGuardTest {
    @Test fun statefulPagerMayReuseCursorWhileItemsAdvance() {
        val guard = RemotePlaylistPagingGuard()
        repeat(100) { page -> guard.recordPage(page * 20, (page + 1) * 20, true, "same-pager-id") }
        assertFalse(guard.hasFailed)
    }

    @Test fun duplicatePagesAndCyclingCursorsCannotLoopForever() {
        val guard = RemotePlaylistPagingGuard(maxStagnantPages = 3)
        guard.recordPage(20, 20, true, "cursor-a")
        guard.recordPage(20, 20, true, "cursor-b")
        try {
            guard.recordPage(20, 20, true, "cursor-a")
            fail("An endlessly repeating page must stop")
        } catch (_: RemotePlaylistPaginationException) { assertTrue(guard.hasFailed) }
    }

    @Test fun occasionalEmptyPagesAreAllowedAndGrowthResetsStallCounter() {
        val guard = RemotePlaylistPagingGuard(maxStagnantPages = 3)
        repeat(20) { page ->
            guard.recordPage(page, page, true, "opaque-pager")
            guard.recordPage(page, page, true, "opaque-pager")
            guard.recordPage(page, page + 1, true, "opaque-pager")
        }
        guard.recordPage(20, 20, false, null)
        assertFalse(guard.hasFailed)
    }

    @Test fun paginationNeedsACursorAndIsBoundedEvenForGrowingMalformedFeeds() {
        val missing = RemotePlaylistPagingGuard()
        try { missing.recordPage(0, 20, true, null); fail("Missing continuation") }
        catch (_: RemotePlaylistPaginationException) { assertTrue(missing.hasFailed) }
        val endless = RemotePlaylistPagingGuard(maxPages = 3)
        endless.recordPage(0, 1, true, "a")
        endless.recordPage(1, 2, true, "b")
        try { endless.recordPage(2, 3, true, "c"); fail("Unbounded pagination") }
        catch (_: RemotePlaylistPaginationException) { assertTrue(endless.hasFailed) }
    }

    @Test fun oldRequestsCannotMutateDifferentPlaylistOrNewerVisitToSamePlaylist() {
        val detail = RemotePlaylistDetailUiState(playlist = PlaylistUiModel("a", "A", "", emptyList()))
        assertTrue(remotePlaylistRequestMatches(detail, "a", 4, 4))
        assertFalse(remotePlaylistRequestMatches(detail, "b", 4, 4))
        assertFalse(remotePlaylistRequestMatches(detail, "a", 4, 6))
        assertFalse(remotePlaylistRequestMatches(RemotePlaylistDetailUiState(), "a", 4, 4))
    }

    @Test fun navigationSnapshotRetainsAllLoadedPagesInOriginalOrder() {
        val cache = NavigationContentCache<RemotePlaylistDetailUiState>()
        val videos = (1..90).map { VideoUiModel("v$it", "Video $it", "Creator", "", "1:00") }
        val detail = RemotePlaylistDetailUiState(
            playlist = PlaylistUiModel("a", "A", "", videos.map { it.id }, sourceId = "fixture"),
            videos = videos, continuationId = "page-four", hasMore = true,
        )
        cache.put("a", detail)
        cache.put("b", RemotePlaylistDetailUiState())
        assertEquals(detail, cache.get("a"))
        cache.clear()
        assertNull(cache.get("a"))
    }
}
