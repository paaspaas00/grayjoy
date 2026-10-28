package com.futo.platformplayer.compose.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MusicHomeLiveTest {
    @Test fun publicHomeLoadsRealSectionsAndCacheAvoidsAnotherNetworkRequest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SpotifyHomeRepository(context)
        val first = repository.load(true)
        assertTrue(first.size >= 2)
        assertTrue(first.flatMap { it.items }.any { it.type == "album" || it.type == "playlist" })
        val start = android.os.SystemClock.elapsedRealtime()
        assertEquals(first, repository.load(false))
        val elapsed = android.os.SystemClock.elapsedRealtime() - start
        assertTrue("Cache read took $elapsed ms", elapsed < 1000)
        android.util.Log.i("MusicHomeLiveTest", "${first.size} shelves, ${first.sumOf { it.items.size }} items; cached read ${elapsed}ms")
        Unit
    }
}
