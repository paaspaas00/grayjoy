package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.HomeFeedType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCacheSessionTest {
    private val snapshot = CachedHomeSnapshot(
        HomeFeedType.ForYou,
        mapOf(HomeFeedType.ForYou to CachedHomePage(emptyList(), "live-pager-handle", true)),
        pagerSessionId = "engine-1",
    )

    @Test
    fun cachedRowsSurviveEngineRestartButLiveHandlesDoNot() {
        val restored = snapshot.forPagerSession("engine-2").pages.getValue(HomeFeedType.ForYou)
        assertNull(restored.continuationId)
        assertTrue(restored.hasMore)
        assertSame(snapshot.pages.getValue(HomeFeedType.ForYou).videos, restored.videos)
    }

    @Test
    fun sameEngineKeepsItsLivePagerAndSnapshotIdentity() {
        assertSame(snapshot, snapshot.forPagerSession("engine-1"))
    }

    @Test
    fun diskSnapshotAlsoRequiresPagerRecreation() {
        val restored = snapshot.copy(pagerSessionId = null).forPagerSession("engine-1")
        assertNull(restored.pages.getValue(HomeFeedType.ForYou).continuationId)
    }
}
