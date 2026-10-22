package com.futo.platformplayer.compose

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FullscreenAutoplayLiveTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun fullscreenKeepsTheActualCountdownAndCancelActionVisible(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autoplayLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
        lateinit var model: GrayjayViewModel
        scenario.onActivity { model = (it.application as GrayjoyApplication).playbackViewModel }
        val previous = model.uiState.value.youtubeAutoplayEnabled
        val volume = withContext(Dispatchers.Main) { model.player.volume }
        try {
            withContext(Dispatchers.Main) { model.setYoutubeAutoplayEnabled(true); model.player.volume = 0f }
            withTimeout(90_000) {
                while (!withContext(Dispatchers.Main) { model.player.isPlaying && model.player.duration > 1_000 &&
                    !model.uiState.value.nowPlaying.isLoadingExtras }) delay(100)
            }
            ui.onNodeWithTag("media-player").performClick()
            ui.onNodeWithContentDescription(context.getString(R.string.enter_fullscreen)).performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("fullscreen-player").fetchSemanticsNodes().isNotEmpty() }
            delay(5_000)
            val id = model.uiState.value.playback.currentVideoId
            withContext(Dispatchers.Main) { model.player.play(); model.player.seekTo(model.player.duration - 250) }
            withTimeout(35_000) { while (model.uiState.value.youtubeAutoplay == null) delay(50) }
            val title = model.uiState.value.youtubeAutoplay!!.title
            ui.onNodeWithTag("youtube-autoplay-countdown").assertIsDisplayed()
            ui.onNodeWithText(title).assertIsDisplayed()
            ui.onNodeWithTag("youtube-autoplay-cancel").performClick()
            delay(11_000)
            assertNull(model.uiState.value.youtubeAutoplay)
            assertEquals(id, model.uiState.value.playback.currentVideoId)
        } finally {
            withContext(Dispatchers.Main) { model.closePlayback(); model.setYoutubeAutoplayEnabled(previous); model.player.volume = volume }
            scenario.close()
        }
    }
}
