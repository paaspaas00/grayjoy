package com.futo.platformplayer.backend

import com.futo.platformplayer.api.media.structures.IPager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GrayjayPluginBackendTest {
    @Test
    fun pluginSignatureState_treatsMissingAndBlankPairsAsUnsigned() {
        assertEquals(PluginSignatureState.Unsigned, pluginSignatureState(null, null))
        assertEquals(PluginSignatureState.Unsigned, pluginSignatureState("", ""))
        assertEquals(PluginSignatureState.Unsigned, pluginSignatureState("  ", "\t"))
    }

    @Test
    fun pluginSignatureState_requiresBothSignatureFields() {
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState("signature", null))
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState(null, "public-key"))
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState("signature", ""))
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState("", "public-key"))
    }

    @Test
    fun pluginSignatureState_recognizesCompleteSignedPayload() {
        assertEquals(
            PluginSignatureState.Signed,
            pluginSignatureState("signature", "public-key"),
        )
    }

    @Test
    fun interleaveSourceResults_keepsEverySourceVisibleNearTop() {
        val merged = interleaveSourceResults(
            listOf(
                listOf("youtube-1", "youtube-2", "youtube-3"),
                listOf("odysee-1", "odysee-2"),
                listOf("custom-1"),
            ),
        )

        assertEquals(
            listOf(
                "youtube-1",
                "odysee-1",
                "custom-1",
                "youtube-2",
                "odysee-2",
                "youtube-3",
            ),
            merged,
        )
    }

    @Test
    fun interleaveSourceResults_keepsResultsWhenSomeSourcesAreEmpty() {
        assertEquals(
            listOf("odysee-1", "odysee-2"),
            interleaveSourceResults(listOf(emptyList(), listOf("odysee-1", "odysee-2"))),
        )
    }

    @Test
    fun drainUniquePager_stopsAfterRepeatedEmptyOrDuplicatePages() = runBlocking {
        val pager = RepeatingPager(listOf("first"))

        val results = drainUniquePager(
            pager = pager,
            maxItems = 100,
            maxPages = 2_000,
            maxConsecutiveEmptyPages = 2,
            itemOf = { it as? String },
            keyOf = { it },
        )

        assertEquals(listOf("first"), results)
        assertEquals(2, pager.nextPageCount)
        assertEquals(3, pager.resultReadCount)
    }

    @Test
    fun drainUniquePager_keepsAdvancingWhilePagesAddItems() = runBlocking {
        val pager = FinitePager(
            listOf(
                listOf("one"),
                listOf("two"),
                listOf("three"),
            ),
        )

        val results = drainUniquePager(
            pager = pager,
            maxItems = 100,
            maxPages = 2_000,
            maxConsecutiveEmptyPages = 2,
            itemOf = { it as? String },
            keyOf = { it },
        )

        assertEquals(listOf("one", "two", "three"), results)
    }

    @Test
    fun drainUniquePager_doesNotSwallowCancellation() = runBlocking {
        val pager = object : IPager<String> {
            override fun hasMorePages() = true
            override fun nextPage() = Unit
            override fun getResults(): List<String> = throw CancellationException("cancelled")
        }

        try {
            drainUniquePager(
                pager = pager,
                maxItems = 100,
                maxPages = 2_000,
                maxConsecutiveEmptyPages = 2,
                itemOf = { it as? String },
                keyOf = { it },
            )
            fail("CancellationException should escape the pager drain")
        } catch (_: CancellationException) {
            // Expected: cancellation must terminate the account import immediately.
        }
    }

    private class RepeatingPager<T>(
        private val results: List<T>,
    ) : IPager<T> {
        var nextPageCount = 0
            private set
        var resultReadCount = 0
            private set

        override fun hasMorePages() = true

        override fun nextPage() {
            nextPageCount += 1
        }

        override fun getResults(): List<T> {
            resultReadCount += 1
            return results
        }
    }

    private class FinitePager<T>(
        private val pages: List<List<T>>,
    ) : IPager<T> {
        private var index = 0

        override fun hasMorePages() = index < pages.lastIndex

        override fun nextPage() {
            if (hasMorePages()) index += 1
        }

        override fun getResults(): List<T> = pages.getOrElse(index) { emptyList() }
    }
}
