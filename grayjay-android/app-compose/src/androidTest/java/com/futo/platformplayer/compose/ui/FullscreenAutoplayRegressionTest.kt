package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.FullscreenPlayerScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FullscreenAutoplayRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }
    @Test fun nextTitleAndCancellationStayVisibleAfterTransportControlsHide() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val countdown = mutableStateOf<YoutubeAutoplayUiState?>(null)
        var cancelled = false
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalYoutubeAutoplay provides YoutubeAutoplayControls(countdown.value, {
                cancelled = true; countdown.value = null
            })) {
                FullscreenPlayerScreen(
                    VideoUiModel("fixture", "Video", "Channel", "", "1:00"), player, PlaybackUiState(), false,
                    onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {}, onSeek = {},
                    onSpeedChange = {}, onQualityChange = {}, onCaptionsEnabledChange = {},
                    onSubtitleLanguageChange = {}, onRetryPlayback = {}, onExitFullscreen = {}, portraitFullscreen = true,
                )
            }
        } }
        rule.mainClock.advanceTimeBy(4_000)
        rule.runOnIdle { countdown.value = YoutubeAutoplayUiState("Next title stays visible", 10) }
        rule.onNodeWithTag("youtube-autoplay-countdown").assertIsDisplayed()
        rule.onNodeWithText("Next title stays visible").assertIsDisplayed()
        rule.runOnIdle { countdown.value = countdown.value?.copy(seconds = 8) }
        rule.onNodeWithTag("youtube-autoplay-cancel").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(cancelled) }
        rule.onNodeWithTag("youtube-autoplay-countdown").assertDoesNotExist()
    }
}
