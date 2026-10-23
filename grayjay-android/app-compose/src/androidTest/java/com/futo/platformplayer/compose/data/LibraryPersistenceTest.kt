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
        appContext.deleteSharedPreferences("grayjay_compose_preferences_$profileId")
    }

    @Test fun historyOriginSurvivesSparseRefreshDownloadsAndCheckpointsButNotANewStandalonePlay() {
        val repository = SharedPreferencesLibraryRepository(context, profileId)
        val origin = com.futo.platformplayer.compose.ui.HistoryPlaylistOrigin("remote-list", "youtube", "Road trip", "run-1")
        val video = VideoUiModel("origin-video", "Track", "Creator", "", "1:00", historyPlaylistOrigin = origin)
        repository.recordHistory(video, watchedAt = 1234)
        repository.saveVideo(video.copy(historyPlaylistOrigin = null, title = "Updated title"))
        repository.saveDownloadDescriptor(video.copy(historyPlaylistOrigin = null, playbackUrl = "file:///fixture"))
        repository.setWatchProgress(video.id, .7f)
        val saved = SharedPreferencesLibraryRepository(context, profileId).loadSavedVideos().single()
        assertEquals(origin, saved.historyPlaylistOrigin)
        assertEquals(1234L, saved.lastWatchedAt)
        repository.recordHistory(video.copy(historyPlaylistOrigin = null), watchedAt = 2345)
        assertNull(repository.loadSavedVideos().single().historyPlaylistOrigin)
        repository.recordHistory(video, watchedAt = 3456)
        repository.setWatchLater(video.id, true)
        repository.removeFromHistory(listOf(video.id))
        val removed = SharedPreferencesLibraryRepository(context, profileId).loadSavedVideos().single()
        assertEquals(0L, removed.lastWatchedAt)
        assertNull(removed.historyPlaylistOrigin)
    }

    @Test fun historyGroupingPreferenceIsOptInAndPersistsPerProfile() {
        val first = com.futo.platformplayer.compose.GrayjayPreferences(context, profileId)
        org.junit.Assert.assertFalse(first.groupHistoryByPlaylist)
        first.groupHistoryByPlaylist = true
        assertTrue(com.futo.platformplayer.compose.GrayjayPreferences(context, profileId).groupHistoryByPlaylist)
        first.groupHistoryByPlaylist = false
        org.junit.Assert.assertFalse(com.futo.platformplayer.compose.GrayjayPreferences(context, profileId).groupHistoryByPlaylist)
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

    @Test
    fun autoQueuePlaylistKeepsItsIdentityAndLastAdditionAcrossReloads() {
        fun video(id: String) = VideoUiModel(id, id, "Creator", "", "1:00")
        val first = SharedPreferencesLibraryRepository(context, profileId)
        val playlist = first.createPlaylist("Played fixture", listOf(video("one")), isPlaybackQueue = true)!!
        first.addVideosToPlaylist(playlist.id, listOf(video("two")))
        val reopened = SharedPreferencesLibraryRepository(context, profileId)
        assertTrue(reopened.loadPlaylists().single().isPlaybackQueue)
        val updated = reopened.addVideosToPlaylist(playlist.id, listOf(video("three"), video("three")))!!
        assertEquals(listOf("one", "two", "three"), updated.videoIds)
        assertEquals(3, updated.videoCount)
        assertEquals(updated.videoIds, first.loadPlaylists().single().videoIds)
        assertEquals(listOf("Played fixture"), first.loadSavedVideos().first { it.id == "three" }.playlistNames)
    }

    @Test
    fun musicClassificationPersistsAndSparseRefreshCannotEraseIt() {
        val repository = SharedPreferencesLibraryRepository(context, profileId)
        fun video(id: String, isMusic: Boolean?) = VideoUiModel(id, id, "Creator", "", "1:00", isMusic = isMusic)
        repository.saveVideos(listOf(video("music", true), video("talk", false), video("unknown", null)))
        repository.recordHistory(video("music", null))
        repository.saveVideo(video("talk", null))
        val saved = SharedPreferencesLibraryRepository(context, profileId).loadSavedVideos().associateBy(VideoUiModel::id)
        assertEquals(true, saved.getValue("music").isMusic)
        assertEquals(false, saved.getValue("talk").isMusic)
        assertNull(saved.getValue("unknown").isMusic)
        repository.saveVideo(video("music", false))
        assertEquals(false, repository.loadSavedVideos().first { it.id == "music" }.isMusic)
    }

    @Test fun truncatedJsonCannotBeOverwrittenByAnOrdinarySave() {
        val store = context.getSharedPreferences("grayjay_compose_library_v2_$profileId", 0)
        for (key in listOf("saved_videos", "playlists")) {
            store.edit().clear().putString(key, "[{\"id\":\"unfinished").commit()
            val repository = SharedPreferencesLibraryRepository(context, profileId)
            repository.loadSavedVideos()
            repository.loadPlaylists()
            try {
                repository.saveVideo(VideoUiModel("new", "New", "Creator", "", "1:00"))
                org.junit.Assert.fail("Unreadable data must not be silently replaced")
            } catch (_: LibraryDataUnavailableException) { }
            assertEquals("[{\"id\":\"unfinished", store.getString(key, null))
        }
    }
}
