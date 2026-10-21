package com.futo.platformplayer.compose

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class PlaybackRefreshIntentLiveTest {
    @Test fun seekAndPauseDuringAudioChangeAndRetryWinOverOldSnapshots(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("playbackRefreshLive") == "true")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: GrayjayViewModel
        scenario.onActivity { model = (it.application as GrayjoyApplication).playbackViewModel }
        val previousVolume = withContext(Dispatchers.Main) { model.player.volume }
        try {
            withContext(Dispatchers.Main) {
                model.player.volume = 0f
                model.openExternalUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            }
            withTimeout(90_000L) { while (!withContext(Dispatchers.Main) { model.player.isPlaying && model.player.duration > 100_000L }) delay(100L) }
            withContext(Dispatchers.Main) {
                model.setAudioLanguage("en")
                model.player.seekTo(60_000L)
                model.player.pause()
            }
            withTimeout(60_000L) { while (model.uiState.value.playback.audioLanguageAutomatic) delay(100L) }
            withContext(Dispatchers.Main) {
                assertFalse("Audio selection restarted paused playback", model.player.playWhenReady)
                assertTrue("Audio selection restored the stale position", model.player.currentPosition >= 59_000L)
                model.player.play()
                model.retryPlayback()
                model.player.seekTo(90_000L)
                model.player.pause()
            }
            withTimeout(60_000L) { while (model.uiState.value.nowPlaying.isLoadingPlayback) delay(100L) }
            withContext(Dispatchers.Main) {
                assertFalse("Retry ignored a later pause", model.player.playWhenReady)
                assertTrue("Retry restored the stale position", model.player.currentPosition >= 89_000L)
                assertFalse("Retry discarded the explicit audio-language selection", model.uiState.value.playback.audioLanguageAutomatic)
            }
        } finally {
            withContext(Dispatchers.Main) { model.closePlayback(); model.player.volume = previousVolume }
            scenario.close()
        }
    }
}
