package com.futo.platformplayer.compose.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.engine.EngineUserImportResult
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryConcurrencyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun independentRepositoriesDoNotLoseConcurrentWrites() = withProfile { profile ->
        val workers = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until 4).map { worker -> workers.submit {
                val repository = SharedPreferencesLibraryRepository(context, profile)
                val preferences = GrayjayPreferences(context, profile)
                start.await()
                repeat(35) { index ->
                    val id = "$worker-$index"
                    repository.saveVideo(video(id))
                    preferences.setCreatorFollowed(id, true)
                }
            } }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            val expected = (0 until 4).flatMap { w -> (0 until 35).map { "$w-$it" } }.toSet()
            assertEquals(expected, SharedPreferencesLibraryRepository(context, profile)
                .loadSavedVideos().map { it.id }.toSet())
            assertEquals(expected, GrayjayPreferences(context, profile).followedCreatorIds())
        } finally { workers.shutdownNow() }
    }

    @Test fun rollbackCannotEraseAnotherRepositorysHistoryOrSubscription() = withProfile { profile ->
        val repository = SharedPreferencesLibraryRepository(context, profile)
        val foreground = SharedPreferencesLibraryRepository(context, profile)
        val preferences = GrayjayPreferences(context, profile)
        val foregroundPreferences = GrayjayPreferences(context, profile)
        repository.saveVideo(video("original"))
        val workers = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        var writing: java.util.concurrent.Future<*>? = null
        try {
            val failing = object : LibraryRepository by repository {
                override fun mergeImportedData(videos: List<VideoUiModel>, playlists: List<PlaylistUiModel>,
                    repairSyntheticHistoryDates: Boolean) {
                    repository.mergeImportedData(videos, playlists, repairSyntheticHistoryDates)
                    writing = workers.submit {
                        started.countDown()
                        foreground.recordHistory(video("watched-during-import"))
                        foregroundPreferences.setCreatorFollowed("followed-during-import", true)
                        finished.countDown()
                    }
                    assertTrue(started.await(5, TimeUnit.SECONDS))
                    assertFalse("A second repository must wait until rollback finishes", finished.await(150, TimeUnit.MILLISECONDS))
                    throw CancellationException("Cancel import after its first write")
                }
            }
            try {
                runBlocking {
                    applyAccountImportTransaction(failing, preferences,
                        EngineUserImportResult(emptyList(), listOf(video("partial")), emptyList(), 1, emptyList()), true)
                }
                fail("Import must propagate cancellation")
            } catch (_: CancellationException) { }
            writing!!.get(10, TimeUnit.SECONDS)
            assertEquals(setOf("original", "watched-during-import"), repository.loadSavedVideos().map { it.id }.toSet())
            assertTrue(preferences.isCreatorFollowed("followed-during-import"))
        } finally { workers.shutdownNow() }
    }

    private fun withProfile(block: (String) -> Unit) {
        val profile = "concurrency-test-${UUID.randomUUID()}"
        try { block(profile) } finally {
            listOf("grayjay_compose_library_v2", "grayjay_compose_watch_progress_v1", "grayjay_compose_preferences")
                .forEach { context.getSharedPreferences("${it}_$profile", 0).edit().clear().commit() }
        }
    }

    private fun video(id: String) = VideoUiModel(id, id, "Creator", "", "1:00")
}
