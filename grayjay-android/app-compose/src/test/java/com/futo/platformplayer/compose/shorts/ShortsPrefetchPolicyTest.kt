package com.futo.platformplayer.compose.shorts

import org.junit.Assert.*
import org.junit.Test

class ShortsPrefetchPolicyTest {
    @Test fun neighborsAreBoundedAndPrioritizeForwardPlayback() {
        assertEquals(listOf("b", "c", "a"), shortsPrefetchWindow(listOf("a", "b", "c", "d"), 1))
        assertEquals(listOf("a", "b"), shortsPrefetchWindow(listOf("a", "b"), 0))
        assertEquals(emptyList<String>(), shortsPrefetchWindow(emptyList(), 0))
    }

    @Test fun rapidSwipesRejectObsoleteCompletionButKeepNearbyReversal() {
        val cache = ShortsResolvedCache<String>()
        val generation = cache.generation
        cache.setWindow(listOf("a", "b"))
        assertTrue(cache.put("a", "resolved-a", 100, 1000, generation))
        cache.setWindow(listOf("c", "d", "b"))
        assertFalse(cache.put("a", "obsolete-a", 101, 1000, generation))
        assertTrue(cache.put("b", "resolved-b", 101, 1000, generation))
        cache.setWindow(listOf("b", "c", "a"))
        assertEquals("resolved-a", cache.get("a", 102))
        assertEquals("resolved-b", cache.get("b", 102))
    }

    @Test fun profileOrSessionChangeRejectsAlreadyRunningWork() {
        val cache = ShortsResolvedCache<String>()
        cache.setWindow(listOf("a"))
        val old = cache.generation
        cache.clear()
        cache.setWindow(listOf("a"))
        assertFalse(cache.put("a", "old-profile", 0, 1000, old))
        assertNull(cache.get("a", 0))
    }

    @Test fun retentionUsesLruAndMonotonicExpiry() {
        val cache = ShortsResolvedCache<String>(2)
        cache.setWindow(listOf("a", "b", "c"))
        cache.put("a", "A", 0, 100, cache.generation)
        cache.put("b", "B", 0, 100, cache.generation)
        cache.get("a", 1)
        cache.put("c", "C", 1, 100, cache.generation)
        assertEquals(setOf("a", "c"), cache.ids())
        assertNull(cache.get("a", 100))
        assertEquals("C", cache.get("c", 100))
    }

    @Test fun signedUrlLifetimeIsNeverExtendedByMemoization() {
        assertEquals(70_000L, shortsDescriptorTtlMs(listOf("https://example.test/media?expire=1100"), 1_000_000))
        assertEquals(0L, shortsDescriptorTtlMs(listOf("https://example.test/media?expire=900"), 1_000_000))
        assertEquals(SHORTS_RESOLVED_TTL_MS, shortsDescriptorTtlMs(listOf("https://example.test/media"), 1_000_000))
        assertEquals(SHORTS_RESOLVED_TTL_MS, shortsDescriptorTtlMs(listOf("https://example.test/media?expire=9223372036854775807"), 1_000_000))
    }

    @Test fun byteBudgetAndGenerationRemainBoundedDuringThousandsOfSwipes() {
        val cache = BoundedMediaPrefixCache(1024)
        val random = java.util.Random(20261009)
        repeat(10_000) {
            val before = cache.generation()
            if (it % 47 == 0) cache.clear()
            val bytes = ByteArray(random.nextInt(512) + 1)
            val accepted = cache.put("video-$it", BoundedMediaPrefixCache.Prefix(bytes, 10_000, null), before)
            assertEquals(it % 47 != 0, accepted)
            assertTrue(cache.byteCount() in 0..1024)
        }
        val generation = cache.generation()
        assertFalse(cache.put("oversize", BoundedMediaPrefixCache.Prefix(ByteArray(1025), 1000, null), generation))
    }
}
