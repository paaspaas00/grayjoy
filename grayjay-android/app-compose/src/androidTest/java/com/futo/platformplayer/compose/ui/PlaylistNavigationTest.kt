package com.futo.platformplayer.compose.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.PlaylistDetailScreen
import com.futo.platformplayer.compose.ui.screens.ReorderPlaylistDialog
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI-only fixtures: no repositories, preferences, files, networking or application ViewModel. */
class PlaylistNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val videos = (1..20).map { VideoUiModel("v$it", "Video $it with a longer title that wraps", "Creator", "", "2:00") }
    private val playlist = PlaylistUiModel("fixture", "Fixture", "", videos.map { it.id })

    @Test fun cancelledPredictiveBackKeepsSelectionAndCommittedBackClearsIt() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            PlaylistDetailScreen(playlist, videos, onVideoClick = {}, onVideoLongClick = {}, onPlayAll = {})
        } }
        rule.onNodeWithTag("playlist-detail-fixture").performScrollToNode(hasTestTag("video-card-v1"))
        rule.onNodeWithTag("video-card-v1").performTouchInput { longClick() }
        rule.onNodeWithTag("playlist-selection-bar").assertIsDisplayed()
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT))
        }
        rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 200f, 0.6f, BackEventCompat.EDGE_LEFT))
            rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        rule.onNodeWithTag("playlist-selection-bar").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("playlist-selection-bar").assertDoesNotExist()
    }

    @Test fun currentVideoIndicatorDoesNotAutomaticallyScrollThePlaylist() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            PlaylistDetailScreen(playlist, videos, onVideoClick = {}, onVideoLongClick = {}, onPlayAll = {},
                currentVideoId = "v15")
        } }
        rule.onNodeWithTag("playlist-detail-fixture").performScrollToNode(hasTestTag("video-card-v1"))
        rule.onNodeWithTag("video-card-v1").assertIsDisplayed()
        rule.onNodeWithTag("current-playlist-video", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("playlist-detail-fixture").performScrollToNode(hasTestTag("video-card-v15"))
        rule.onNodeWithTag("video-card-v15").assertIsDisplayed()
        rule.onNodeWithTag("current-playlist-video", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun reorderUsesDragGeometryAndKeepsAllItems() {
        var confirmed: List<String>? = null
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ReorderPlaylistDialog(videos, onDismiss = {}, onConfirm = { confirmed = it })
        } }
        rule.onNodeWithTag("playlist-reorder-handle-v1", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(0f, 240f), durationMillis = 600)
        }
        rule.onNodeWithText(rule.activity.getString(R.string.ok)).performClick()
        rule.runOnIdle {
            assertNotNull(confirmed)
            assertEquals(videos.map { it.id }.toSet(), confirmed!!.toSet())
            assertEquals(videos.size, confirmed!!.size)
            assertTrue(confirmed!!.indexOf("v1") > 0)
        }
    }

    @Test fun stationaryFingerKeepsOneFloatingRowAndAStableSlot() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ReorderPlaylistDialog(videos, onDismiss = {}, onConfirm = {})
        } }
        val list = rule.onNodeWithTag("playlist-reorder-list")
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        val handle = rule.onNodeWithTag("playlist-reorder-handle-v1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val start = handle.center - listBounds.topLeft
        rule.mainClock.autoAdvance = false
        try {
            list.performTouchInput {
                down(start)
                moveTo(Offset(start.x, listBounds.height * 0.5f), delayMillis = 300)
            }
            rule.mainClock.advanceTimeBy(500)
            rule.onAllNodesWithText(videos[0].title, useUnmergedTree = true).assertCountEquals(1)
            val before = rule.onNodeWithTag("playlist-reorder-slot").fetchSemanticsNode().boundsInRoot.top
            val ghostBefore = rule.onNodeWithTag("playlist-reorder-dragged").fetchSemanticsNode().boundsInRoot.top
            rule.mainClock.advanceTimeBy(600)
            assertEquals(before, rule.onNodeWithTag("playlist-reorder-slot").fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals(ghostBefore, rule.onNodeWithTag("playlist-reorder-dragged").fetchSemanticsNode().boundsInRoot.top, 1f)
            list.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(400)
            rule.onNodeWithTag("playlist-reorder-dragged").assertDoesNotExist()
        } finally { rule.mainClock.autoAdvance = true }
    }
}
