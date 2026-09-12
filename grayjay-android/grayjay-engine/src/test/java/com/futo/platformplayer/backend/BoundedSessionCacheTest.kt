package com.futo.platformplayer.backend

import org.junit.Assert.*
import org.junit.Test

class BoundedSessionCacheTest {
    @Test fun browsingManyPagesKeepsRecentAndActivelyReadHandles() {
        val cache = BoundedSessionCache<String, Any>(3)
        val active = Any()
        cache["active"] = active
        cache["old"] = Any()
        cache["new"] = Any()
        assertSame(active, cache["active"])
        cache["next"] = Any()
        assertNull(cache["old"])
        assertSame(active, cache["active"])
        repeat(1_000) { cache["page-$it"] = Any(); cache["active"] }
        assertEquals(3, cache.size)
        assertSame(active, cache["active"])
    }

    @Test fun oldRequestCannotDeleteAReplacementHandle() {
        val cache = BoundedSessionCache<String, Any>(2)
        val old = Any()
        val replacement = Any()
        cache["page"] = old
        cache["page"] = replacement
        assertFalse(cache.remove("page", old))
        assertSame(replacement, cache["page"])
    }
}
