package com.futo.platformplayer.backend

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class NewPipePaginationTest {
    private fun items(from: Int, to: Int): List<InfoItem> = (from..to).map {
        StreamInfoItem(0, "https://www.youtube.com/watch?v=item$it", "Item $it", StreamType.VIDEO_STREAM)
    }

    @Test fun preservesOverflowBetweenSmallUiPagesAndLargeExtractorPages() = runBlocking {
        val backend = NewPipeYoutubeContentBackend { error("No network required") }
        var loads = 0
        val initial = items(1, 100)
        var continuation: String? = backend.registerPager(
            NewPipeYoutubeContentBackend.PagerKind.Playlist, Page("second"),
            loader = { loads++; InfoItemsPage(items(101, 170), null, emptyList()) },
            pendingItems = initial.drop(30), pageSize = 30,
        )
        val received = initial.take(30).toMutableList()
        val firstOverflow = backend.loadPage(continuation!!)
        received += firstOverflow.items
        assertEquals(0, loads)
        continuation = firstOverflow.continuationId
        while (continuation != null) {
            val page = backend.loadPage(continuation)
            assertTrue(page.items.size <= 30)
            received += page.items
            continuation = page.continuationId
        }
        assertEquals((1..170).map { "Item $it" }, received.map { it.name })
        assertEquals(1, loads)
    }

    @Test fun overflowRemainsPageableWithoutANetworkContinuation() = runBlocking {
        val backend = NewPipeYoutubeContentBackend { error("No network required") }
        val id = backend.registerPager(NewPipeYoutubeContentBackend.PagerKind.ChannelPlaylists, null,
            loader = { error("Buffered items must not fetch another page") }, pendingItems = items(31, 40), pageSize = 30)!!
        val page = backend.loadPage(id)
        assertEquals(10, page.items.size)
        assertFalse(page.hasMore)
    }

    @Test fun repeatedPagesEndThePagerButDifferentPostBodiesRemainDistinct() = runBlocking {
        val backend = NewPipeYoutubeContentBackend { error("No network required") }
        var step = 0
        val id = backend.registerPager(NewPipeYoutubeContentBackend.PagerKind.Comments,
            Page("https://www.youtube.com/api", byteArrayOf(1)), loader = {
                step++
                InfoItemsPage<InfoItem>(emptyList(), Page("https://www.youtube.com/api", byteArrayOf(2)), emptyList())
            })!!
        assertTrue(backend.loadPage(id).hasMore)
        assertFalse(backend.loadPage(id).hasMore)
        assertEquals(2, step)
    }

    @Test fun resetDuringReadDoesNotReturnAnObsoletePage() = runBlocking {
        val backend = NewPipeYoutubeContentBackend { error("No network required") }
        val id = backend.registerPager(NewPipeYoutubeContentBackend.PagerKind.Search, Page("first"), loader = {
            backend.resetTransientSessions()
            InfoItemsPage(items(1, 2), Page("second"), emptyList())
        })!!
        val failure = runCatching { backend.loadPage(id) }.exceptionOrNull()
        assertTrue(failure is SourceSessionChangedException)
        assertTrue(runCatching { backend.loadPage(id) }.exceptionOrNull() is SourceSessionChangedException)
    }

    @Test fun commentContinuationRetainsVideoUrlAcrossPages() = runBlocking {
        val backend = NewPipeYoutubeContentBackend { error("No network required") }
        val url = "https://www.youtube.com/watch?v=DpJTzdBp09c"
        val id = requireNotNull(backend.registerPager(
            NewPipeYoutubeContentBackend.PagerKind.Comments, Page("first"),
            loader = { InfoItemsPage<InfoItem>(emptyList(), Page("next"), emptyList()) },
            contentUrl = url,
        ))
        assertEquals(url, backend.loadPage(id).contentUrl)
        assertEquals(url, backend.loadPage(id).contentUrl)
    }

    @Test fun cancelledPageDoesNotAdvanceTheContinuation() = runBlocking {
        val backend = NewPipeYoutubeContentBackend { error("No network required") }
        val requestedPages = mutableListOf<String>()
        lateinit var request: Job
        var cancelFirst = true
        val id = requireNotNull(backend.registerPager(
            NewPipeYoutubeContentBackend.PagerKind.Comments, Page("first"),
            loader = { page ->
                requestedPages += page.url
                if (cancelFirst) { cancelFirst = false; request.cancel() }
                InfoItemsPage<InfoItem>(emptyList(), Page("next"), emptyList())
            },
            contentUrl = "video",
        ))
        request = launch(start = CoroutineStart.LAZY) { backend.loadPage(id) }
        request.start()
        request.join()
        backend.loadPage(id)
        assertEquals(listOf("first", "first"), requestedPages)
    }
}
