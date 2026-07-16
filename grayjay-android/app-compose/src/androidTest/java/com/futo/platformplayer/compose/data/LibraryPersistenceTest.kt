package com.futo.platformplayer.compose.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStorage() {
        context.getSharedPreferences("grayjay_compose_library_v2", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
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
        val repository = SharedPreferencesLibraryRepository(context)

        repository.recordHistory(video, progress = 0.4f)
        repository.createPlaylist("Saved", listOf(video))

        val reloaded = SharedPreferencesLibraryRepository(context)
        val savedVideo = reloaded.loadSavedVideos().single()
        assertEquals(video.title, savedVideo.title)
        assertEquals(video.thumbnailUrl, savedVideo.thumbnailUrl)
        assertEquals(0.4f, savedVideo.watchProgress)
        assertTrue(savedVideo.lastWatchedAt > 0L)
        assertEquals(listOf("Saved"), savedVideo.playlistNames)
        assertEquals(listOf(video.id), reloaded.loadPlaylists().single().videoIds)
    }
}
