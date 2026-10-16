package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.backend.YoutubeSubscriptionFetchMode
import com.futo.platformplayer.compose.ui.SearchContentType
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpotifySourceIntegrationTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun publicCatalogSearchPlaylistAndYoutubePlaybackWorkWithoutSpotifyLogin(): Unit = runBlocking {
        lateinit var engine: AndroidGrayjayEngine
        activity.scenario.onActivity { context ->
            engine = AndroidGrayjayEngine(context)
            engine.player.volume = 0f
            engine.configureYoutubeBackend(true, YoutubeSubscriptionFetchMode.Fast)
            engine.registerSources(engine.sources(emptyList()))
            engine.clearSourceCache("spotify")
        }
        try {
            val corpus = SearchCorpus(emptyList(), emptyList(), emptyList())
            val tracks = withTimeout(90_000) {
                engine.search("Radiohead No Surprises", setOf("spotify"), corpus, SearchContentType.Videos)
            }
            assertTrue(tracks.videos.isNotEmpty())
            val spotifyTrack = tracks.videos.first()
            assertEquals("spotify", spotifyTrack.sourceId)
            assertTrue(spotifyTrack.contentUrl.startsWith("https://open.spotify.com/track/"))
            val route = withTimeout(30_000) { engine.routeUrl(spotifyTrack.contentUrl, setOf("spotify")) }
            assertEquals(EngineUrlKind.Video, route?.kind)
            assertEquals("spotify", route?.sourceId)

            val direct = withTimeout(120_000) {
                engine.resolve(VideoUiModel(
                    id = spotifyTrack.contentUrl,
                    title = "",
                    creator = "Spotify",
                    metadata = "",
                    duration = "",
                    sourceId = "spotify",
                    contentUrl = spotifyTrack.contentUrl,
                    shareUrl = spotifyTrack.contentUrl,
                ))
            }
            assertTrue(direct.title.isNotBlank())
            assertTrue(!direct.creator.equals("spotify", ignoreCase = true))
            assertEquals(spotifyTrack.contentUrl, direct.contentUrl)

            val playlists = withTimeout(90_000) {
                engine.search("Radiohead", setOf("spotify"), corpus, SearchContentType.Playlists)
            }
            assertTrue(playlists.playlists.isNotEmpty())
            val playlist = withTimeout(60_000) { engine.loadPlaylist(playlists.playlists.first()) }
            assertTrue(playlist.videos.isNotEmpty())

            for (newPipe in listOf(true, false)) {
                engine.configureYoutubeBackend(newPipe, YoutubeSubscriptionFetchMode.Fast)
                val resolved = withTimeout(120_000) { engine.resolve(spotifyTrack) }
                assertEquals("spotify", resolved.sourceId)
                assertEquals(spotifyTrack.contentUrl, resolved.contentUrl)
                assertEquals(spotifyTrack.title, resolved.title)
                assertTrue(resolved.playbackUrl.isNotBlank() || resolved.playbackManifest.isNotBlank())
            }
        } finally {
            withContext(Dispatchers.Main) { engine.release() }
        }
    }
}
