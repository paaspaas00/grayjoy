package com.futo.platformplayer.backend

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class YoutubeChannelReleasesTest {
    private fun firstPageHtml(token: String = "next") = """var ytInitialData = {
        "channelMetadataRenderer":{"title":"Artist"},
        "tabRenderer":{"endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/@artist/releases"}}},
        "content":{"gridPlaylistRenderer":{"playlistId":"PLone","title":{"simpleText":"Album"}},
        "continuationCommand":{"token":"$token"}}}};
        var ytcfg = {"INNERTUBE_CLIENT_VERSION":"test-version"};"""

    @Test fun repeatedContinuationStopsInsteadOfCreatingAnInfiniteFeed() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val body = if (chain.request().method == "GET") firstPageHtml()
                else """{"gridPlaylistRenderer":{"playlistId":"PLtwo","title":{"simpleText":"Second"}},"continuationCommand":{"token":"next"}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        try {
            val reader = YoutubeChannelReleasesBackend(client)
            val first = reader.firstPage("https://www.youtube.com/@artist")
            val next = reader.loadMore(first.page.continuationId!!)
            assertFalse(next.hasMore)
            assertEquals("PLtwo", next.playlists.single().id)
            assertEquals(next, reader.loadMore(first.page.continuationId!!))
            assertEquals(2, requests.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun clearDuringRequestPreventsOldResultsFromRepopulatingTheCache() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (requests.incrementAndGet() == 1) {
                started.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(firstPageHtml().toResponseBody()).build()
        }.build()
        try {
            val reader = YoutubeChannelReleasesBackend(client)
            val task = async { runCatching { reader.firstPage("https://www.youtube.com/@artist") } }
            withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
            reader.clear()
            release.countDown()
            assertTrue(task.await().exceptionOrNull() is SourceSessionChangedException)
            assertTrue(reader.firstPage("https://www.youtube.com/@artist").available)
            assertEquals(2, requests.get())
        } finally { release.countDown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun rejectsExcessiveJsonNestingBeforeAllocatingADeepTree() {
        for (depth in listOf(101, 1_000, 10_000)) {
            assertTrue(runCatching { parseBoundedYoutubeJson("[".repeat(depth) + "]".repeat(depth)) }.isFailure)
        }
        assertTrue(runCatching { parseYoutubeInitialData("var ytInitialData = null; function unrelated(){ }") }.isFailure)
        assertTrue(runCatching { parseBoundedYoutubeJson("[" + List(100_001) { "0" }.joinToString(",") + "]") }.isFailure)
        assertEquals("[[]]", parseBoundedYoutubeJson("[[]]").toString())
    }

    @Test fun percentEncodedHandleCannotTurnIntoQueryParameters() {
        assertEquals("https://www.youtube.com/@a%3Fb/releases?hl=en", youtubeReleasesUrl("https://www.youtube.com/@a%3Fb/videos"))
    }

    @Test fun normalizesOnlyYoutubeChannelUrls() {
        assertEquals("https://www.youtube.com/@artist/releases?hl=en", youtubeReleasesUrl("https://www.youtube.com/@artist/videos?x=1"))
        assertEquals("https://www.youtube.com/channel/UC123/releases?hl=en", youtubeReleasesUrl("https://youtube.com/channel/UC123/playlists"))
        for (url in listOf("https://evil.example/@artist", "https://youtube.com/watch?v=x", "file:///tmp/artist")) {
            assertTrue(runCatching { youtubeReleasesUrl(url) }.isFailure)
        }
    }
    @Test fun readsEscapedBracesInInitialDataWithoutConsumingFollowingScripts() {
        val json = parseYoutubeInitialData("""<script>var ytInitialData = {"title":"Album } {","nested":{"x":1}}; console.log("later");</script>""")
        assertEquals("Album } {", json.get("title").asString)
    }
    @Test fun readsBothLegacyAndModernAlbumCardsAndDeduplicates() {
        val json = JsonParser.parseString("""{"items":[
            {"gridPlaylistRenderer":{"playlistId":"PLone","title":{"runs":[{"text":"Album One"}]},"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/one.jpg","width":100}]}}},
            {"lockupViewModel":{"contentId":"PLtwo","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","metadata":{"lockupMetadataViewModel":{"title":{"content":"Single Two"}}}}},
            {"playlistRenderer":{"playlistId":"PLone","title":{"simpleText":"Duplicate"}}},
            {"lockupViewModel":{"contentId":"video","contentType":"LOCKUP_CONTENT_TYPE_VIDEO","metadata":{"lockupMetadataViewModel":{"title":{"content":"Not an album"}}}}}
        ]}""")
        val items = parseYoutubeReleaseItems(json, "Artist")
        assertEquals(listOf("PLone", "PLtwo"), items.map { it.id })
        assertEquals(listOf("Album One", "Single Two"), items.map { it.title })
        assertTrue(items.all { it.author == "Artist" && it.videoCount == -1 })
        assertEquals("https://i.ytimg.com/one.jpg", items[0].thumbnailUrl)
    }
    @Test fun malformedPageFailsInsteadOfLooping() {
        assertTrue(runCatching { parseYoutubeInitialData("var ytInitialData = {") }.isFailure)
        assertTrue(parseYoutubeReleaseItems(JsonParser.parseString("{}"), "").isEmpty())
    }
    @Test fun liveReleasesViaBothReaders() = runBlocking {
        assumeTrue(System.getenv("GRAYJOY_LIVE_TESTS") == "1")
        val channel = "https://www.youtube.com/@madonna"
        val reader = YoutubeChannelReleasesBackend(OkHttpClient())
        val native = reader.firstPage(channel)
        assertTrue("Native Releases tab not detected", native.available)
        assertTrue("Native Releases list is empty", native.page.playlists.isNotEmpty())
        println("Native Releases: ${native.page.playlists.size} albums/singles; paging=${native.page.hasMore}")
        val playback = NewPipeYoutubePlaybackBackend()
        val backend = NewPipeYoutubeContentBackend(playback::ensureInitialized)
        val details = backend.loadChannel("youtube", channel)
        assertTrue("NewPipe Releases tab not detected", details.supportsReleases)
        val albums = backend.loadChannelPage(channel, "RELEASES")
        assertTrue("NewPipe Releases list is empty", albums.playlists.isNotEmpty())
        println("NewPipe Releases: ${albums.playlists.size} albums/singles; paging=${albums.hasMore}")
        if (native.page.hasMore) {
            val next = reader.loadMore(native.page.continuationId!!)
            assertTrue("Native next release page is empty", next.playlists.isNotEmpty())
            println("Native next page: ${next.playlists.size} releases")
        }
        if (albums.hasMore) {
            val next = backend.loadMoreChannelPage(albums.continuationId!!)
            assertTrue("NewPipe next release page is empty", next.playlists.isNotEmpty())
            println("NewPipe next page: ${next.playlists.size} releases")
        }
    }
}
