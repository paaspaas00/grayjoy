package com.futo.platformplayer.compose.data

import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PlaylistPlaybackPreferencesTest {
    @Test fun recentRemotePlaylistsAndShuffleSurviveReloadWithoutLeakingBetweenProfiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "playlist-test-${UUID.randomUUID()}"
        val other = "$id-other"
        try {
            val store = PlaylistPlaybackPreferences(context, id)
            repeat(8) { store.record(PlaylistUiModel("p$it", "Playlist $it", "", listOf("video"), sourceId = "spotify")) }
            store.record(PlaylistUiModel("p4", "Playlist 4", "", emptyList(), sourceId = "spotify"))
            store.setShuffled("p4", true)
            val reloaded = PlaylistPlaybackPreferences(context, id)
            assertEquals(listOf("p4", "p7", "p6", "p5", "p3"), reloaded.recent().map { it.id })
            assertTrue(reloaded.recent().all { it.sourceId == "spotify" && it.videoIds.isEmpty() })
            assertEquals(setOf("p4"), reloaded.shuffledIds())
            assertTrue(PlaylistPlaybackPreferences(context, other).recent().isEmpty())
            assertTrue(PlaylistPlaybackPreferences(context, other).shuffledIds().isEmpty())
            reloaded.setShuffled("p4", false)
            assertTrue(reloaded.shuffledIds().isEmpty())
        } finally {
            context.deleteSharedPreferences("playlist_playback_$id")
            context.deleteSharedPreferences("playlist_playback_$other")
        }
    }
}
