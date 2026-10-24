package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.CommentRepliesSheet
import com.futo.platformplayer.compose.ui.screens.PlayerTimelineRow
import com.futo.platformplayer.compose.ui.screens.playerSeekPreviewPlacement
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
        fun pixel() = rule.onNodeWithTag("player-buffered-track", useUnmergedTree = true).captureToImage().toPixelMap().let {
            it[(it.width * .7f).toInt(), it.height / 2].red
        }
        val before = pixel()
        rule.runOnUiThread { bufferedMs = 90_000L }
        android.os.SystemClock.sleep(600)
        rule.mainClock.advanceTimeBy(600)
        assertTrue("The buffered track must update without transport events", pixel() > before + .1f)
    }

    @Test fun tallerSeekAreaAcceptsTouchesAboveTrackWithoutMovingVisibleBar() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var touchHeight by mutableStateOf(48.dp)
        val seeks = mutableListOf<Float>()
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            Box(Modifier.size(360.dp, 480.dp).background(Color.Black)) {
                PlayerTimelineRow(VideoUiModel("seek-test", "Test", "", "", "1:40"), player!!, 0L,
                    100_000L, 0, emptyList(), true, true, { seeks += it }, {},
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    seekTouchTargetHeight = touchHeight)
            }
        } }
        val originalTrack = rule.onNodeWithTag("player-buffered-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { touchHeight = 96.dp } // One fifth of this 480 dp player.
        val tallerTrack = rule.onNodeWithTag("player-buffered-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(originalTrack.top, tallerTrack.top, 1f)
        assertEquals(originalTrack.height, tallerTrack.height, 1f)
        rule.onNodeWithTag("player-seek-touch-area").assertHeightIsEqualTo(96.dp)
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput { click(Offset(width * .75f, 4f)) }
        rule.runOnIdle { assertTrue(seeks.last() in 0.65f..0.85f) }
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput {
            swipe(Offset(width * .2f, 4f), Offset(width * .6f, 4f), 300)
        }
        rule.runOnIdle { assertTrue(seeks.last() in 0.5f..0.7f) }
    }

    @Test fun seekHandleUsesTheTrackCoordinatesAtStartMiddleAndEnd() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var position by mutableStateOf(0L)
        var rtl by mutableStateOf(false)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                Box(Modifier.size(360.dp, 150.dp).background(Color.Black)) {
                    PlayerTimelineRow(VideoUiModel("geometry", "Test", "", "", "1:40"), player!!, position,
                        100_000L, 80, emptyList(), true, true, {}, {},
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), seekTouchTargetHeight = 96.dp)
                }
            }
        } }
        for (direction in listOf(false, true)) for (fraction in listOf(0f, .1f, .5f, .9f, 1f)) {
            rule.runOnIdle { rtl = direction; position = (fraction * 100_000).toLong() }
            val track = rule.onNodeWithTag("player-buffered-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val handle = rule.onNodeWithTag("player-seek-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val expectedX = if (direction) track.right - track.width * fraction else track.left + track.width * fraction
            assertEquals("Handle and progress use the same horizontal origin", expectedX, handle.center.x, 1.5f)
            assertEquals("Handle is vertically centered on the track", track.center.y, handle.center.y, 1.5f)
        }
        rule.runOnIdle { rtl = false; position = 50_000L }
        rule.mainClock.advanceTimeBy(300)
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "seek-handle-aligned.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun seekPreviewIsAboveTheBarOnItsVeryFirstPlacementAndAfterResizing() {
        var progress by mutableStateOf(.5f)
        var touchHeight by mutableStateOf(96.dp)
        var previewHeight by mutableStateOf(160.dp)
        var rtl by mutableStateOf(false)
        val placements = mutableListOf<Rect>()
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                Box(Modifier.size(360.dp, 480.dp)) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(touchHeight).testTag("preview-area")) {
                        Box(Modifier.playerSeekPreviewPlacement(progress, touchHeight)
                            .onGloballyPositioned {
                                val origin = it.positionInRoot()
                                placements += Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                            }
                            .size(128.dp, previewHeight))
                    }
                }
            }
        } }
        fun assertPlacements() {
            val area = rule.onNodeWithTag("preview-area").fetchSemanticsNode().boundsInRoot
            val density = rule.density
            val expectedBottom = area.top + with(density) { (touchHeight - 48.dp - 6.dp).toPx() }
            assertTrue("The preview must have been placed", placements.isNotEmpty())
            placements.forEach { preview ->
                assertEquals("Even the first placement must be above the seek bar", expectedBottom, preview.bottom, 1.5f)
                assertTrue(preview.left >= area.left && preview.right <= area.right)
                val expectedCenter = area.left + area.width * (if (rtl) 1f - progress else progress)
                assertEquals("Preview must follow the finger, also in RTL", expectedCenter, preview.center.x, 3f)
            }
        }
        assertPlacements()
        for (direction in listOf(false, true)) for (height in listOf(48.dp, 96.dp)) {
            rule.runOnIdle {
                placements.clear()
                rtl = direction
                progress = .3f
                touchHeight = height
                previewHeight = if (height == 48.dp) 90.dp else 160.dp
            }
            assertPlacements()
        }
    }

    @Test fun storyboardFollowsSeekWithoutChangingTheBarGeometry() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val sprite = java.io.File(rule.activity.cacheDir, "seek-preview-test.png")
        val bitmap = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(50, 110, 150))
        sprite.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val video = VideoUiModel("storyboard-geometry", "Test", "", "", "1:40",
            storyboard = StoryboardUiModel(listOf(StoryboardLevelUiModel(
                160, 90, 1, 1, 1, 100_000L, sprite.toURI().toString()))))
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            Box(Modifier.size(360.dp, 480.dp).background(Color.Black)) {
                PlayerTimelineRow(video, player!!, 0L, 100_000L, 80, emptyList(), true, true, {}, {},
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), seekTouchTargetHeight = 96.dp)
            }
        } }
        val track = rule.onNodeWithTag("player-buffered-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput {
            down(Offset(width * .2f, centerY))
            moveTo(Offset(width * .4f, centerY))
        }
        val firstPreview = rule.onNodeWithTag("player-seek-preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("player-seek-storyboard", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(firstPreview.height > 100f)
        assertTrue(firstPreview.bottom < track.top)
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput { moveTo(Offset(width * .8f, centerY)) }
        val nextPreview = rule.onNodeWithTag("player-seek-preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(firstPreview.top, nextPreview.top, 1f)
        assertEquals(firstPreview.bottom, nextPreview.bottom, 1f)
        assertTrue(nextPreview.left >= firstPreview.left)
        assertEquals(track, rule.onNodeWithTag("player-buffered-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "seek-preview-stable.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        rule.onNodeWithTag("player-seek-touch-area").performTouchInput { up() }
        rule.onNodeWithTag("player-seek-preview", useUnmergedTree = true).assertDoesNotExist()
        sprite.delete()
    }
}
