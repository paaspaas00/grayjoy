package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryPersistenceTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var context: Context
    private lateinit var profileId: String

    @Before
    fun isolateStorage() {
        profileId = "instrumentation-${UUID.randomUUID()}"
        context = object : ContextWrapper(appContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(name.endsWith("_$profileId")) { "Test attempted to access non-isolated storage" }
                return super.getSharedPreferences(name, mode)
            }
        }
    }

    @After
    fun removeIsolatedStorage() {
        appContext.deleteSharedPreferences("grayjay_compose_library_v2_$profileId")
        appContext.deleteSharedPreferences("grayjay_compose_watch_progress_v1_$profileId")
    }

    @Test
    fun historyAndPlaylistContainFullVideosAfterRepositoryReload() {
        val video = VideoUiModel(
            id = "https://example.com/watch/123",
            title = "Persisted plugin video",
            creator = "Creator",
            metadata = "12 views",
            duration = "2:00",
            sourceId = "youtube",
            contentUrl = "https://example.com/watch/123",
            thumbnailUrl = "https://example.com/thumb.jpg",
            sourceName = "YouTube",
            sourceIconUrl = "https://plugins.grayjay.app/Youtube/youtube.png",
        )
        val repository = SharedPreferencesLibraryRepository(context, profileId)

        repository.recordHistory(video, progress = 0.4f)
        repository.createPlaylist("Saved", listOf(video))

        val reloaded = SharedPreferencesLibraryRepository(context, profileId)
        val savedVideo = reloaded.loadSavedVideos().single()
        assertEquals(video.title, savedVideo.title)
        assertEquals(video.thumbnailUrl, savedVideo.thumbnailUrl)
        assertEquals(0.4f, savedVideo.watchProgress)
        assertTrue(savedVideo.lastWatchedAt > 0L)
        assertEquals(listOf("Saved"), savedVideo.playlistNames)
        assertEquals(listOf(video.id), reloaded.loadPlaylists().single().videoIds)
    }

    @Test
    fun duplicatePlaylistNamesAreRejectedAndSelectedPlaylistsCanBeRemoved() {
        val repository = SharedPreferencesLibraryRepository(context, profileId)
        val video = VideoUiModel(
            id = "video-one",
            title = "Video one",
            creator = "Creator",
            metadata = "Now",
            duration = "1:00",
        )
        val created = repository.createPlaylist("Road Trip", listOf(video))

        assertNull(repository.createPlaylist("  road   trip  ", listOf(video)))
        assertEquals(1, repository.loadPlaylists().size)
        assertEquals(1, repository.removePlaylists(listOf(created!!.id)))
        assertTrue(repository.loadPlaylists().isEmpty())
        assertTrue(repository.loadSavedVideos().single().playlistNames.isEmpty())
    }
}
