package com.futo.platformplayer.compose.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.engine.EngineUserImportResult
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountImportTransactionTest {
    @Test
    fun cancelledCommitRestoresLibraryAndSubscriptions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profileId = "transaction-test-${UUID.randomUUID()}"
        val repository = SharedPreferencesLibraryRepository(context, profileId)
        val preferences = GrayjayPreferences(context, profileId)
        val original = video("original")
        repository.saveVideo(original)
        preferences.setCreatorFollowed("original-channel", true)

        val failingRepository = object : LibraryRepository by repository {
            override fun mergeImportedData(
                videos: List<VideoUiModel>,
                playlists: List<PlaylistUiModel>,
                repairSyntheticHistoryDates: Boolean,
            ) {
                repository.mergeImportedData(videos, playlists, repairSyntheticHistoryDates)
                throw CancellationException("cancel during commit")
            }
        }
        val importedChannel = ChannelUiModel(
            id = "imported-channel",
            name = "Imported",
            sourceId = "youtube",
            source = "YouTube",
            unreadCount = 0,
            followerCount = "Creator",
            description = "",
        )
        val result = EngineUserImportResult(
            subscriptions = listOf(importedChannel),
            videos = listOf(video("imported")),
            playlists = listOf(PlaylistUiModel("imported-list", "Imported", "", listOf("imported"))),
            historyCount = 1,
            warnings = emptyList(),
        )

        try {
            applyAccountImportTransaction(
                repository = failingRepository,
                preferences = preferences,
                result = result,
                repairSyntheticHistoryDates = true,
            )
        } catch (_: CancellationException) {
            // Expected: assertions below verify that cancellation was transactional.
        }

        assertEquals(listOf(original.id), repository.loadSavedVideos().map(VideoUiModel::id))
        assertTrue(preferences.isCreatorFollowed("original-channel"))
        assertFalse(preferences.isCreatorFollowed(importedChannel.id))
        assertTrue(preferences.loadImportedChannels().isEmpty())
    }

    private fun video(id: String) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        contentUrl = "https://www.youtube.com/watch?v=$id",
        lastWatchedAt = 1_700_000_000_000L,
    )
}
