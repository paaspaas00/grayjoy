package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationStateRetentionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun protectedOldRootPageSurvivesManyVisitsAndStateRestoration() {
        val route = mutableStateOf("root-home")
        val protection = mutableStateOf(setOf("root-home"))
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val holder = rememberBoundedPageStateHolder(maxEntries = 3, protectedKeys = protection.value)
            holder.SaveableStateProvider(route.value) {
                var value by rememberSaveable { mutableStateOf(0) }
                TextButton(onClick = { value++ }, modifier = Modifier.testTag("counter")) { Text("Count $value") }
            }
        }
        rule.onNodeWithTag("counter").performClick()
        repeat(15) { index -> rule.runOnIdle { route.value = "channel-$index" } }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { route.value = "root-home" }
        rule.onNodeWithText("Count 1").assertIsDisplayed()
        rule.runOnIdle { protection.value = emptySet() }
        repeat(5) { index -> rule.runOnIdle { route.value = "new-channel-$index" } }
        rule.runOnIdle { route.value = "root-home" }
        rule.onNodeWithText("Count 0").assertIsDisplayed()
    }

    @Test fun oldRouteStateIsEvictedAndRecentRoutesSurviveRecreation() {
        val route = mutableStateOf("route-0")
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val holder = rememberBoundedPageStateHolder(maxEntries = 3)
            holder.SaveableStateProvider(route.value) {
                var value by rememberSaveable { mutableStateOf(0) }
                TextButton(onClick = { value++ }, modifier = Modifier.testTag("counter")) { Text("Count $value") }
            }
        }
        for (index in 0..3) {
            rule.runOnIdle { route.value = "route-$index" }
            rule.onNodeWithTag("counter").performClick()
        }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { route.value = "route-2" }
        rule.onNodeWithText("Count 1").assertIsDisplayed()
        rule.runOnIdle { route.value = "route-0" }
        rule.onNodeWithText("Count 0").assertIsDisplayed()
    }

    @Test fun refreshedChannelCannotAddVideosNoLongerInTheResults() {
        val channel = ChannelUiModel("fixture", "Creator", "youtube", "YouTube", 0, "", "")
        val videos = (1..3).map { VideoUiModel("video-$it", "Video $it", "Creator", "", "1:00") }
        val state = mutableStateOf(ChannelDetailUiState(channelId = channel.id, channel = channel, videos = videos))
        var added = emptyList<String>()
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ChannelDetailScreen(channel, state.value, false, {}, onTabSelected = {}, onPlaylistClick = {},
                onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {}, onAddSelectionToPlaylist = { added = it })
        } }
        val list = rule.onNodeWithTag("channel-detail-fixture")
        list.performScrollToNode(hasTestTag("video-card-video-1"))
        rule.onNodeWithTag("video-card-video-1").performTouchInput { longClick() }
        list.performScrollToNode(hasTestTag("video-card-video-2"))
        rule.onNodeWithTag("video-card-video-2").performClick()
        rule.runOnIdle { state.value = state.value.copy(videos = videos.drop(1)) }
        rule.onNodeWithTag("channel-selection-add").performClick()
        rule.runOnIdle { assertEquals(listOf("video-2"), added) }
        rule.runOnIdle { state.value = state.value.copy(videos = emptyList()) }
        rule.onNodeWithTag("channel-selection-bar").assertDoesNotExist()
    }
}
