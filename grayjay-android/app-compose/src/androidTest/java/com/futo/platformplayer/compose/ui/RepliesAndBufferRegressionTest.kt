package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.CommentRepliesSheet
import com.futo.platformplayer.compose.ui.screens.PlayerTimelineRow
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RepliesAndBufferRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    @After fun release() { rule.runOnUiThread { player?.release() } }

    @Test fun repeatedReplySwipesDoNotStartAnAutonomousSheetOscillation() {
        val replies = (0..39).map { VideoCommentUiModel(id = "r$it", author = "Author $it", message = "Reply text. ".repeat(18), age = "Today") }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CommentRepliesSheet(CommentRepliesUiState(replies = replies, isVisible = true), {}, {})
        } }
        rule.onNodeWithTag("comment-replies-list").performTouchInput { swipeUp() }
        repeat(6) { index ->
            rule.onNodeWithTag("comment-replies-list").performTouchInput {
                val x = width * (0.3f + index * 0.06f)
                swipe(androidx.compose.ui.geometry.Offset(x, height * .65f),
                    androidx.compose.ui.geometry.Offset(x, height * (if (index % 2 == 0) .35f else .8f)), 400)
            }
        }
        rule.waitForIdle()
        val initial = rule.onNodeWithTag("comment-replies-sheet").fetchSemanticsNode().boundsInRoot
        repeat(12) {
            rule.mainClock.advanceTimeBy(400)
            val current = rule.onNodeWithTag("comment-replies-sheet").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.top, current.top, 1f)
            assertEquals(initial.height, current.height, 1f)
        }
    }

    @Test fun bufferAdvancesWithoutAnyPausePlayEventOrRootStateChange() {
        var bufferedMs = 10_000L
        lateinit var observed: ForwardingPlayer
        rule.runOnUiThread {
            player = ExoPlayer.Builder(rule.activity).build()
            observed = object : ForwardingPlayer(player!!) {
                override fun getCurrentPosition() = 1_000L
                override fun getBufferedPosition() = bufferedMs
            }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            Box(Modifier.background(Color.Black)) {
            PlayerTimelineRow(VideoUiModel("test", "Test", "", "", "1:40"), observed, null,
                100_000L, 0, emptyList(), false, false, {}, {})
            }
        } }
        fun pixel() = rule.onNodeWithTag("player-buffered-track").captureToImage().toPixelMap().let {
            it[(it.width * .7f).toInt(), it.height / 2].red
        }
        val before = pixel()
        rule.runOnUiThread { bufferedMs = 90_000L }
        android.os.SystemClock.sleep(600)
        rule.mainClock.advanceTimeBy(600)
        assertTrue("The buffered track must update without transport events", pixel() > before + .1f)
    }
}
