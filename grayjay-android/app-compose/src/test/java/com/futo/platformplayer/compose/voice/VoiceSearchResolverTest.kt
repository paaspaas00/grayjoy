package com.futo.platformplayer.compose.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoiceSearchResolverTest {
    private data class Video(val id: String, val title: String, val views: Long)
    @Test fun highestViewsWinsAmongRelevantResults() = runBlocking {
        val videos = listOf(Video("cover", "Queen Bohemian Rhapsody cover", 50),
            Video("official", "Queen Bohemian Rhapsody official video", 2_000_000),
            Video("unrelated", "Funny cats", 9_000_000_000))
        assertEquals("official", resolveVoiceYoutubeSearch("Queen Bohemian Rhapsody", { videos }, { emptyList() },
            Video::id, Video::title, Video::views)?.id)
    }
    @Test fun completedDownloadAvoidsAllNetworkRequestsAndAllowsArtistTitleOrder() = runBlocking {
        val local = Video("saved", "John Lennon Imagine", 5)
        assertEquals(local, resolveVoiceYoutubeSearch("Imagine di John Lennon", { error("Network search must not run") },
            { error("Suggestions must not run") }, Video::id, Video::title, Video::views, { listOf(local) }))
    }
    @Test fun unrelatedDownloadDoesNotHijackRequestedTitle() = runBlocking {
        val match = Video("online", "Queen Bohemian Rhapsody", 500)
        assertEquals(match, resolveVoiceYoutubeSearch("Queen Bohemian Rhapsody", { listOf(match) }, { emptyList() },
            Video::id, Video::title, Video::views, { listOf(Video("wrong", "Cat compilation", Long.MAX_VALUE)) }))
    }
    @Test fun phoneticSuggestionRecoversAnEmptySearchWithoutManyRequests() = runBlocking {
        val searches = mutableListOf<String>()
        val chosen = resolveVoiceYoutubeSearch("boiemian rapsodi", search = { query ->
            searches.add(query)
            if (query == "Bohemian Rhapsody") listOf("Queen – Bohemian Rhapsody (Official Video)") else emptyList()
        }, suggestions = { listOf("Bohemian Rhapsody", "cats") }, id = { it }, title = { it })
        assertEquals("Queen – Bohemian Rhapsody (Official Video)", chosen)
        assertEquals(listOf("boiemian rapsodi", "Bohemian Rhapsody"), searches)
    }
    @Test fun unavailableSuggestionsKeepExistingYoutubeResults() = runBlocking {
        assertEquals("Queen – Bohemian Rhapsody", resolveVoiceYoutubeSearch("boiemian rapsodi", search = { listOf("Queen – Bohemian Rhapsody") },
            suggestions = { error("offline") }, id = { it }, title = { it }))
    }
    @Test fun exactTitleNeedsNoSecondSearchAndBreaksTiesInYoutubeOrder() = runBlocking {
        var calls = 0
        val videos = listOf("Metallica – One official", "Metallica – One live")
        assertEquals(videos.first(), resolveVoiceYoutubeSearch("Metallica One", search = { calls++; videos },
            suggestions = { listOf("Metallica One guitar") }, id = { it }, title = { it }))
        assertEquals(1, calls)
    }
    @Test fun slowBlockingSuggestionsCannotDelayAnAlreadyGoodMatch() = runBlocking {
        val started = System.nanoTime()
        val entered = java.util.concurrent.CountDownLatch(1)
        assertEquals("Bohemian Rhapsody", resolveVoiceYoutubeSearch("Bohemian Rhapsody",
            search = { assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)); listOf("Bohemian Rhapsody") },
            suggestions = { entered.countDown(); Thread.sleep(1800); emptyList() }, id = { it }, title = { it }))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1000)
    }
}
