package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.screens.ShortsFeedPlayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShortsFeedPlayerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun feedPrependReorderAndPagingChangesCannotSelectAnotherPlayingShort() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        fun videos(vararg ids: String) = ids.map { VideoUiModel(it, it, "Creator", "", "0:10", isShort = true) }
        val feed = mutableStateOf(videos("a", "b", "c"))
        val active = mutableStateOf("b")
        val shown = mutableStateOf(true)
        val hasMore = mutableStateOf(false)
        val selections = mutableListOf<String>()
        try {
            rule.setContent {
                if (shown.value) ShortsFeedPlayer(
                    videos = feed.value, activeVideoId = active.value, player = player,
                    onVideoSelected = { selections += it.id; active.value = it.id },
                    hasMore = hasMore.value, onLoadMore = {},
                ) { Text("Playing ${active.value}") }
            }
            rule.onNodeWithText("Playing b").assertIsDisplayed()
            rule.runOnIdle { feed.value = videos("new", "a", "b", "c") }
            rule.onNodeWithText("Playing b").assertIsDisplayed()
            rule.runOnIdle {
                assertTrue(selections.isEmpty())
                feed.value = videos("c", "b", "new", "a")
            }
            rule.onNodeWithText("Playing b").assertIsDisplayed()
            rule.runOnIdle {
                hasMore.value = true
                feed.value = videos("c", "b", "new", "a", "d", "e")
            }
            rule.onNodeWithText("Playing b").assertIsDisplayed()
            rule.runOnIdle {
                assertTrue(selections.isEmpty())
                assertEquals("b", active.value)
                assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
            }
            // A genuine gesture still selects the neighbor in the newly anchored feed order.
            rule.onNodeWithTag("shorts-fullscreen-pager").performTouchInput { swipeUp() }
            rule.onNodeWithText("Playing new").assertIsDisplayed()
            rule.runOnIdle { assertEquals(listOf("new"), selections) }
        } finally {
            rule.runOnIdle { shown.value = false }
            rule.runOnUiThread { player.release() }
        }
    }

    @Test fun swipesBothDirectionsAndRestoresRepeatModeOnExit() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val active = mutableStateOf("a")
        val shown = mutableStateOf(true)
        val videos = listOf("a", "b", "c").map { VideoUiModel(it, it, "Creator", "", "0:10", isShort = true) }
        try {
            rule.setContent { if (shown.value) ShortsFeedPlayer(videos, active.value, player, { active.value = it.id }, false, {}) { Text("Playing ${active.value}") } }
            rule.runOnIdle { assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode) }
            rule.onNodeWithTag("shorts-fullscreen-pager").performTouchInput { swipeUp() }
            rule.onNodeWithText("Playing b").assertIsDisplayed()
            rule.onNodeWithTag("shorts-fullscreen-pager").performTouchInput { swipeDown() }
            rule.onNodeWithText("Playing a").assertIsDisplayed()
            rule.runOnIdle { shown.value = false }
            rule.runOnIdle { assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode) }
        } finally { rule.runOnUiThread { player.release() } }
    }
}
