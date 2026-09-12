package com.futo.platformplayer.compose.casting

import java.io.IOException
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CastRoutingTest {
    @Test fun repeatedAndSlidingLiveManifestsStayBounded() {
        val routes = CastRouteRegistry<String>(64)
        val generation = routes.reset()
        val first = routes.add("segment", generation, "stream")
        repeat(10_000) { assertEquals(first, routes.add("segment", generation, "stream")) }
        assertEquals(1, routes.size)
        repeat(10_000) { start ->
            repeat(6) { offset -> routes.add("segment-${start + offset}", generation, "stream") }
            assertTrue(routes.size <= 64)
        }
        routes.reset()
        assertThrows(IOException::class.java) { routes.add("old", generation, "stream") }
        assertEquals(0, routes.size)
    }

    @Test fun playlistTypeDoesNotDependOnFileExtension() {
        val targets = mutableListOf<Pair<String, Boolean>>()
        val manifest = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,URI="audio?token=1"
            #EXT-X-STREAM-INF:BANDWIDTH=1000
            video?token=2
            #EXT-X-KEY:METHOD=AES-128,URI="key"
            #EXTINF:6,
            segment.ts
        """.trimIndent()
        rewriteHlsPlaylist(manifest, "https://example.org/live/master") { uri, playlist ->
            targets += uri to playlist
            "local"
        }
        assertEquals(listOf(true, true, false, false), targets.map { it.second })
        assertEquals("https://example.org/live/audio?token=1", targets.first().first)
    }

    @Test fun rangeParserHandlesSuffixAndRejectsReversedOrOverflowingRanges() {
        assertEquals(CastByteRange(10, 11), parseCastByteRange("bytes=10-20"))
        assertEquals(CastByteRange(10), parseCastByteRange("bytes=10-"))
        assertEquals(CastByteRange(suffix = 512), parseCastByteRange("bytes=-512"))
        for (bad in listOf("10-20", "bytes=20-10", "bytes=-0", "bytes=0-${Long.MAX_VALUE}", "bytes=0-1,3-4")) {
            assertNull(bad, parseCastByteRange(bad))
        }
    }

    @Test fun fuzzRangeArithmeticAgainstBigIntegers() {
        val random = Random(0x52414e47)
        repeat(30_000) {
            val start = random.nextLong()
            val end = random.nextLong()
            val result = parseCastByteRange("bytes=$start-$end")
            if (result != null) {
                assertTrue(start >= 0 && end >= start)
                val expected = BigInteger.valueOf(end) - BigInteger.valueOf(start) + BigInteger.ONE
                assertEquals(expected, BigInteger.valueOf(requireNotNull(result.length)))
                assertTrue(result.length > 0)
            }
        }
    }

    @Test fun partialResponseMatchesDeliveredBytesAndCannotOverflow() {
        assertEquals("bytes 2-4/10", castContentRange(CastByteRange(2, 3), 3, "bytes 2-9/10"))
        assertEquals("bytes 7-9/10", castContentRange(CastByteRange(suffix = 3), 3, "bytes 7-9/10"))
        assertEquals("bytes 2-4/*", castContentRange(CastByteRange(2, 3), 3, null))
        assertEquals("bytes 2-9/10", castContentRange(CastByteRange(2), -1, "bytes 2-9/10"))
        assertNull(castContentRange(CastByteRange(2), -1, null))
        assertNull(castContentRange(CastByteRange(suffix = 3), 3, null))
        assertNull(castContentRange(CastByteRange(2), 0, "bytes 2-9/10"))
        assertNull(castContentRange(CastByteRange(Long.MAX_VALUE), 2, null))
        assertNull(castContentRange(CastByteRange(2), 10, "bytes 2-9/10"))
    }
}
