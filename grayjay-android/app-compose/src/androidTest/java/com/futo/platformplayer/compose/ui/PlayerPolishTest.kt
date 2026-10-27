package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.playback.*
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlayerPolishTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    @After fun cleanup() { rule.runOnUiThread { player?.release() } }

    @Test fun wizardOffersBothLayoutsWithoutEnablingTheExperimentByDefault() {
        var state by mutableStateOf(GrayjayUiState())
        var changes = 0
        val actions = GrayjayAppActions().apply { onOneHandedPlayerChange = {
            changes++; state = state.copy(oneHandedPlayerEnabled = it)
        } }
        val step = guidedSetupSteps(false, false).indexOf(SetupStep.PlayerLayout)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(state, actions, step, {}, {}, { it() })
        } }
        rule.onNodeWithTag("setup-player-standard").assertIsSelected()
        assertEquals(0, changes)
        rule.onNodeWithTag("setup-player-one-handed").performClick().assertIsSelected()
        assertTrue(state.oneHandedPlayerEnabled)
        capture("oct27-wizard-player-layout")
        rule.onNodeWithTag("setup-player-standard").performClick().assertIsSelected()
        assertFalse(state.oneHandedPlayerEnabled)
        assertEquals(2, changes)
    }

    @Test fun channelSettingsAreVerticallyCenteredOnTheAvatar() {
        val channel = ChannelUiModel("channel", "Example channel", "youtube", "YouTube", 0, "10K", "A channel description")
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, ChannelDetailUiState(channelId = channel.id, channel = channel),
                false, {}, onTabSelected = {}, onPlaylistClick = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
        } }
        val avatar = rule.onNodeWithTag("channel-header-avatar").fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithTag("channel-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(avatar.center.y, settings.center.y, 1f)
        capture("oct27-channel-settings-alignment")
    }

    @Test fun playingVideoPausesWhileScrubbingThenSeeksOnceAndResumes() = verifySeek(true, false)
    @Test fun pausedVideoStaysPausedAfterScrubbing() = verifySeek(false, false)
    @Test fun cancelledScrubResumesWithoutCommittingAPosition() = verifySeek(true, true)

    private fun verifySeek(wasPlaying: Boolean, cancel: Boolean) {
        val identity = SeekPreviewIdentity("v", "test", 1, 1, false)
        val guard = SeekPreviewPause()
        val events = mutableListOf<String>()
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            player = ExoPlayer.Builder(rule.activity).build().apply {
                setMediaItem(MediaItem.Builder().setMediaId("v").setUri("asset:///fixture").build())
                playWhenReady = wasPlaying
            }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalSeekPreviewPlayback provides { id, active ->
                if (active) { events += "begin"; if (guard.begin(identity, player!!.playWhenReady)) player!!.pause() }
                else { if (guard.finish(id, identity)) player!!.play(); events += "end" }
            }) {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    PlayerSurface(VideoUiModel("v", "Example", "Creator", "", "1:00"), player!!,
                        PlaybackUiState(durationMs = 60_000, currentVideoId = "v", queueVideoIds = listOf("before", "v", "after")),
                        false, false, canGoPrevious = true, canGoNext = true,
                        onTogglePlayback = {}, onSkipPrevious = {}, onSkipNext = {}, onSeekBy = {},
                        onSeek = { events += "seek" }, onSpeedChange = {}, onQualityChange = {},
                        onCaptionsEnabledChange = {}, onSubtitleLanguageChange = {}, onRetryPlayback = {}, onFullscreen = {},
                        modifier = Modifier.fillMaxWidth().height(250.dp))
                }
            }
        } }
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput {
            down(Offset(width * .25f, height * .5f)); moveTo(Offset(width * .6f, height * .5f), 120)
        }
        rule.runOnIdle { assertFalse(player!!.playWhenReady); assertEquals(0, events.count { it == "seek" }) }
        rule.onNodeWithTag("player-seek-blur").assertExists()
        val transport = rule.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
        val surface = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        assertEquals(surface.center.y - 8 * rule.activity.resources.displayMetrics.density, transport.center.y, 2f)
        if (wasPlaying && !cancel) capture("oct27-seeking-controls")
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput { if (cancel) cancel() else up() }
        rule.mainClock.advanceTimeBy(250)
        rule.runOnIdle {
            assertEquals(wasPlaying, player!!.playWhenReady)
            assertEquals(if (cancel) 0 else 1, events.count { it == "seek" })
        }
        rule.onNodeWithTag("player-seek-blur").assertDoesNotExist()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(120)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {
            File("/sdcard/Download/$name.png").outputStream().use { output -> it.compress(Bitmap.CompressFormat.PNG, 100, output) }
            it.recycle()
        }
    }
}
