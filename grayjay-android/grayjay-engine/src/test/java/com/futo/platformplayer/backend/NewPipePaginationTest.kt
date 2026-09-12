package com.futo.platformplayer.backend

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page

class NewPipePaginationTest {
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
