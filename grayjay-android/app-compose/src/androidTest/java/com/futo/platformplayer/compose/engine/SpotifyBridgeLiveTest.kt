package com.futo.platformplayer.compose.engine

import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.backend.YoutubeSubscriptionFetchMode
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in network check; no playback, download enqueue, account access or library writes. */
class SpotifyBridgeLiveTest {
    @Test fun sourceSpecificProfileCanResolvePublicAudioWithoutExposingYoutube(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSpotify") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var engine: AndroidGrayjayEngine
        instrumentation.runOnMainSync { engine = AndroidGrayjayEngine(context) }
        try {
            engine.setProfile("bridge-test-${UUID.randomUUID()}")
            engine.registerSources(engine.sources(emptyList()).filter { it.id == "spotify" })
            val track = VideoUiModel("https://open.spotify.com/track/fixture", "Killing In The Name", "Rage Against The Machine", "", "5:13", sourceId = "spotify")
            for (newPipe in listOf(true, false)) {
            engine.configureYoutubeBackend(newPipe, YoutubeSubscriptionFetchMode.Fast)
            engine.configureYoutubePlaybackEngine(newPipe)
            val start = System.currentTimeMillis()
            val result = engine.resolve(track, "it", true, EngineResolvePriority.Download)
            assertEquals(track.id, result.id); assertEquals("spotify", result.sourceId)
            assertTrue(result.playbackUrl.isNotBlank() || result.audioUrl.isNotBlank())
            android.util.Log.i("SpotifyBridgeLiveTest", "Public audio resolved (prefer NewPipe=$newPipe) in ${System.currentTimeMillis() - start} ms")
            }
        } finally { instrumentation.runOnMainSync { engine.release() } }
    }
}
