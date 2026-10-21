package com.futo.platformplayer.compose.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryCorruptPreferencesTest {
    @Test fun wrongTypedLibraryValuesDoNotCrashReadsOrPermitDestructiveWrites() = withLibrary { context, profile ->
        val preferences = context.getSharedPreferences("grayjay_compose_library_v2_$profile", 0)
        preferences.edit().putBoolean("saved_videos", true).putInt("playlists", 42).commit()
        val repository = SharedPreferencesLibraryRepository(context, profile)
        assertTrue(repository.loadSavedVideos().isEmpty())
        assertTrue(repository.loadPlaylists().isEmpty())
        assertThrows(LibraryDataUnavailableException::class.java) { repository.saveVideo(video()) }
        assertThrows(LibraryDataUnavailableException::class.java) { repository.createPlaylist("New", listOf(video())) }
        assertEquals(true, preferences.all["saved_videos"])
        assertEquals(42, preferences.all["playlists"])
    }

    @Test fun invalidCacheRevisionDoesNotHideVideosAndIsRepairedByTheNextProgressWrite() = withLibrary { context, profile ->
        val repository = SharedPreferencesLibraryRepository(context, profile)
        repository.saveVideo(video())
        val progress = context.getSharedPreferences("grayjay_compose_watch_progress_v1_$profile", 0)
        progress.edit().putString("__progress_revision", "damaged revision").putFloat("fixture", 0.25f).commit()
        val reopened = SharedPreferencesLibraryRepository(context, profile)
        assertEquals(0.25f, reopened.loadSavedVideos().single().watchProgress, 0f)
        reopened.setWatchProgress("fixture", 0.75f)
        assertEquals(0.75f, SharedPreferencesLibraryRepository(context, profile).loadSavedVideos().single().watchProgress, 0f)
        assertEquals(1L, progress.getLong("__progress_revision", -1L))
    }

    private fun video() = VideoUiModel("fixture", "Fixture", "Creator", "", "1:00")
    private fun withLibrary(block: (Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = "corrupt-library-${UUID.randomUUID()}"
        try { block(context, profile) } finally {
            listOf("grayjay_compose_library_v2", "grayjay_compose_watch_progress_v1", "grayjay_compose_preferences")
                .forEach { context.deleteSharedPreferences("${it}_$profile") }
        }
    }
}
