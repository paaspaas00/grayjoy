package com.futo.platformplayer.compose.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryExternalUpdatesTest {
    @Test fun foregroundCacheSeesWorkerWritesAndDoesNotOverwriteThem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = "cache-audit-${UUID.randomUUID()}"
        val foreground = SharedPreferencesLibraryRepository(context, profile)
        val worker = SharedPreferencesLibraryRepository(context, profile)
        try {
            foreground.saveVideo(video("original"))
            foreground.createPlaylist("First", listOf(video("original")))
            foreground.loadSavedVideos()
            foreground.loadPlaylists()
            worker.saveVideo(video("imported"))
            worker.createPlaylist("Imported", listOf(video("imported")))
            assertEquals(setOf("original", "imported"), foreground.loadSavedVideos().map { it.id }.toSet())
            assertEquals(setOf("First", "Imported"), foreground.loadPlaylists().map { it.title }.toSet())
            foreground.setWatchLater("original", true)
            assertTrue(worker.loadSavedVideos().first { it.id == "original" }.isWatchLater)
            assertTrue(worker.loadSavedVideos().any { it.id == "imported" })
            foreground.setWatchProgress("original", 0.75f)
            assertEquals(0.75f, worker.loadSavedVideos().first { it.id == "original" }.watchProgress, 0f)
            foreground.saveVideo(video("nonfinite").copy(watchProgress = Float.NaN))
            assertEquals(0f, worker.loadSavedVideos().first { it.id == "nonfinite" }.watchProgress, 0f)
        } finally {
            context.getSharedPreferences("grayjay_compose_library_v2_$profile", 0).edit().clear().commit()
            context.getSharedPreferences("grayjay_compose_watch_progress_v1_$profile", 0).edit().clear().commit()
        }
    }

    private fun video(id: String) = VideoUiModel(id, id, "Creator", "", "1:00")
}
