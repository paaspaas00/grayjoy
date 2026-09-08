package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.CompactSettingRow
import com.futo.platformplayer.compose.ui.screens.CompactChannelSearchField
import com.futo.platformplayer.compose.ui.screens.CompactPlaylistHeader
import com.futo.platformplayer.compose.ui.screens.PlaylistMenuAction
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompactComponentsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactChannelSearchRetainsEditingClearAndKeyboardAction() {
        val query = androidx.compose.runtime.mutableStateOf("")
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompactChannelSearchField(query.value, { query.value = it }, 35)
        } }
        val field = rule.onNode(hasSetTextAction())
        field.performClick().assertIsFocused().performTextInput("Terra")
        rule.runOnIdle { assertEquals("Terra", query.value) }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.clear_video_search)).performClick()
        rule.runOnIdle { assertEquals("", query.value) }
        field.performClick().performTextInput("Luna")
        field.performImeAction()
        field.assertIsNotFocused()
        rule.runOnIdle { assertEquals("Luna", query.value) }
    }

    @Test fun primaryVideoTargetsAndCreatorTargetsStayDistinct() {
        var openedVideos = 0
        var openedChannels = 0
        val selected = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.futo.platformplayer.compose.ui.screens.LocalVideoCreatorClick provides { openedChannels++ },
            ) {
                com.futo.platformplayer.compose.ui.screens.CompactVideoCard(
                    VideoUiModel("targets", "Short title", "Creator", "Views · today", "1:00"), 0,
                    onClick = { openedVideos++ }, onLongClick = { selected.value = true },
                    selectionMode = selected.value, animateEntrance = false,
                )
            }
        } }
        rule.onNodeWithTag("video-title-targets", useUnmergedTree = true).performTouchInput { click(center) }
        rule.onNodeWithTag("video-thumbnail-targets", useUnmergedTree = true).performTouchInput { click(center) }
        rule.onNodeWithTag("video-channel-footer-targets").performTouchInput { click(center) }
        assertEquals(2, openedVideos)
        assertEquals(1, openedChannels)
        rule.onNodeWithTag("video-channel-footer-targets").performTouchInput { longClick(center) }
        rule.onNodeWithTag("video-channel-footer-targets").performTouchInput { click(center) }
        assertEquals(3, openedVideos)
        assertEquals(1, openedChannels)
    }

    @Test fun abbreviatedExplanationRemainsAccessibleWithoutChangingTheSetting() {
        val description = "A complete explanation that should remain available when the summary does not fit. ".repeat(10)
        var clicks = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompactSettingRow("Setting", description, onClick = { clicks++ }) { Text("Value") }
        } }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.details)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.ok)).assertIsDisplayed().performClick()
        assertEquals(0, clicks)
        rule.onNodeWithText("Setting").performClick()
        assertEquals(1, clicks)
    }

    @Test fun movingSecondaryActionsIntoTheMenuRetainsEnabledStateAndCallback() {
        var downloads = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompactPlaylistHeader("Playlist", "", true, "fixture-play", {}, listOf(
                PlaylistMenuAction("Download audio", Icons.Outlined.Download, true, tag = "audio", onClick = { downloads++ }),
                PlaylistMenuAction("Download video", Icons.Outlined.Download, false, tag = "video", onClick = { error("Disabled action") }),
            ))
        } }
        rule.onNodeWithTag("playlist-more-actions").performClick()
        rule.onNodeWithTag("video").assertIsNotEnabled()
        rule.onNodeWithTag("audio").performClick()
        assertEquals(1, downloads)
        rule.onNodeWithTag("audio").assertDoesNotExist()
    }
}
