package com.futo.platformplayer.compose

import org.junit.Assert.*
import org.junit.Test

class NavigationContentCacheTest {
    @Test fun returningToParentRetainsWholePagedSnapshotAndRefreshesRecency() {
        val cache = NavigationContentCache<List<Int>>(maxEntries = 2)
        val parent = (0..500).toList()
        cache.put("parent", parent)
        cache.put("child", listOf(1))
        assertSame(parent, cache.get("parent"))
        cache.put("second-child", listOf(2))
        assertNull(cache.get("child"))
        assertSame(parent, cache.get("parent"))
    }

    @Test fun expiredBackendSessionsAndProfileChangesCannotBeRestored() {
        var now = 100L
        val cache = NavigationContentCache<String>(retentionMs = 10, clock = { now })
        cache.put("channel", "previous backend")
        now += 11
        assertNull(cache.get("channel"))
        cache.put("channel", "other account")
        cache.clear()
        assertNull(cache.get("channel"))
        cache.put("channel", "clock reset")
        now = 0
        assertNull(cache.get("channel"))
    }
}
