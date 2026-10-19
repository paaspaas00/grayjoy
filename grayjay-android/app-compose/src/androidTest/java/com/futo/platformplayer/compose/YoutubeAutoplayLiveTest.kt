package com.futo.platformplayer.compose

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in device probe; normal test runs use deterministic offline fixtures. */
class YoutubeAutoplayLiveTest {
    @Test fun realPlaybackWaitsAndCanBeCancelledWithoutSavingAPlaylist(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autoplayLive") == "true")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: GrayjayViewModel
        scenario.onActivity { model = (it.application as GrayjoyApplication).playbackViewModel }
        val previous = withContext(Dispatchers.Main) { model.uiState.value.youtubeAutoplayEnabled }
        val previousVolume = withContext(Dispatchers.Main) { model.player.volume }
        val playlistIds = withContext(Dispatchers.Main) { model.uiState.value.playlists.map { it.id } }
        try {
            withContext(Dispatchers.Main) {
                model.setYoutubeAutoplayEnabled(true)
                model.player.volume = 0f
            }
            suspend fun openAndFinish(): String {
                withContext(Dispatchers.Main) {
                    model.closePlayback()
                    model.openExternalUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                }
                withTimeout(90_000L) { while (!withContext(Dispatchers.Main) {
                    model.player.isPlaying && model.player.duration > 1_000L && !model.uiState.value.nowPlaying.isLoadingExtras
                }) delay(100L) }
                delay(4_000L)
                val id = withContext(Dispatchers.Main) { model.uiState.value.playback.currentVideoId!! }
                withContext(Dispatchers.Main) { model.player.seekTo(model.player.duration - 300L) }
                withTimeout(30_000L) { while (model.uiState.value.youtubeAutoplay == null) delay(100L) }
                return id
            }
            val first = openAndFinish()
            assertTrue(model.uiState.value.youtubeAutoplay!!.seconds in 9..10)
            delay(2_000L)
            assertEquals(first, model.uiState.value.playback.currentVideoId)
            withContext(Dispatchers.Main) { model.cancelYoutubeAutoplay() }
            delay(11_000L)
            assertNull(model.uiState.value.youtubeAutoplay)
            assertEquals(first, model.uiState.value.playback.currentVideoId)
            val second = openAndFinish()
            withTimeout(25_000L) { while (model.uiState.value.playback.currentVideoId == second) delay(100L) }
            assertNull(model.uiState.value.youtubeAutoplay)
            assertEquals(playlistIds, model.uiState.value.playlists.map { it.id })
        } finally {
            withContext(Dispatchers.Main) {
                model.closePlayback()
                model.setYoutubeAutoplayEnabled(previous)
                model.player.volume = previousVolume
            }
            scenario.close()
        }
    }
}
