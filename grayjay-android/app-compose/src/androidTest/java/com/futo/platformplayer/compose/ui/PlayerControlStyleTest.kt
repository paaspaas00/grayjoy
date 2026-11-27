package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PlayerControlStyleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val video = VideoUiModel("control-style", "Playback controls", "Example channel", "", "2:00")
    @Before fun setup() = rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
    @After fun cleanup() = rule.runOnUiThread { player.release() }
    @Test fun fullscreenButtonsHaveCorrectSizesAndHudNeverOverlapsThem() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            CompositionLocalProvider(LocalInteractionControls provides InteractionControls(volumeGesture = true, brightnessGesture = true)) {
                FullscreenPlayerScreen(video, player,
                    PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf("before", video.id, "after")), false,
                    onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {}, onSeek = {},
                    onSpeedChange = {}, onQualityChange = {}, onCaptionsEnabledChange = {},
                    onSubtitleLanguageChange = {}, onRetryPlayback = {}, onExitFullscreen = {},
                    portraitFullscreen = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp })
            }
        } }
        rule.onNodeWithTag("player-play-pause").assertWidthIsEqualTo(144.dp).assertHeightIsEqualTo(144.dp)
        for (tag in listOf("player-previous", "player-next")) rule.onNodeWithTag(tag).assertWidthIsEqualTo(68.dp)
        val maximum = rule.activity.getSystemService(android.media.AudioManager::class.java)
        val oldVolume = maximum.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        try {
            rule.onNodeWithTag("media-player").performTouchInput {
                // Start in empty video space, not on the portrait lock/settings rail.
                val x = width * if (width > height) .9f else .72f
                swipe(Offset(x, height * .65f), Offset(x, height * .45f), 400)
            }
            val hud = rule.onNodeWithTag("player-level-indicator").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            for (tag in listOf("player-play-pause", "player-previous", "player-next")) {
                assertFalse("HUD overlaps $tag", hud.overlaps(rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot))
            }
            capture("player-fullscreen-hud-style")
            val indicator = rule.onNodeWithTag("player-level-indicator").captureToImage().toPixelMap()
            assertTrue("HUD needs a dark contrasting background", indicator[indicator.width / 2, 5].red < .4f)
        } finally { maximum.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, oldVolume, 0) }
    }
    @Test fun inlinePlaylistButtonsKeepTheirPreviousCompactSize() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            PlayerSurface(video, player, PlaybackUiState(queueVideoIds = listOf("before", video.id, "after")), false, false,
                canGoPrevious = true, canGoNext = true, onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {},
                onSeekBy = {}, onSeek = {}, onSpeedChange = {}, onQualityChange = {}, onCaptionsEnabledChange = {},
                onSubtitleLanguageChange = {}, onRetryPlayback = {}, onFullscreen = {},
                modifier = Modifier.fillMaxWidth().height(240.dp))
        } }
        rule.onNodeWithTag("player-play-pause").assertWidthIsEqualTo(86.dp)
        rule.onNodeWithTag("player-previous").assertWidthIsEqualTo(60.dp)
        rule.onNodeWithTag("player-next").assertWidthIsEqualTo(60.dp)
        capture("player-inline-button-style")
    }
    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
