package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.diagnostics.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class OctoberUxRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    private val preferenceName = "ux-${UUID.randomUUID()}"
    @After fun clean() {
        player?.let { rule.runOnUiThread { it.release() } }
        rule.activity.deleteSharedPreferences(preferenceName + "grayjoy_advanced_options")
    }
    private fun startPlayer(): ExoPlayer {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity).build() }
        return player!!
    }

    @Test fun channelSearchHidesTheTopBarAndBackOrDownwardSwipeRevealsIt() {
        val player = startPlayer()
        val channel = ChannelUiModel("channel", "The Linux Experiment", "youtube", "YouTube", 0, "", "Description")
        val videos = List(30) { VideoUiModel("test-$it", "Video ${it + 1}", channel.name, "2 hours ago", "3:00", sourceId = "youtube") }
        var state by mutableStateOf(GrayjayUiState(channels = listOf(channel), videos = videos,
            channelDetail = ChannelDetailUiState(channelId = channel.id, channel = channel, videos = videos, isLoaded = true,
                supportsShorts = true, supportsPlaylists = true),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Channel, channel.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onChannelSearchQueryChange = { state = state.copy(channelDetail = state.channelDetail.copy(searchQuery = it)) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("channel-detail-channel").performScrollToNode(hasTestTag("channel-video-search"))
        rule.onNodeWithTag("channel-video-search").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("browse-top-bar").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("channel-tab-videos").assertIsDisplayed()
        capture("channel-search-pinned")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("browse-top-bar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("channel-detail-channel").assertIsDisplayed()
        rule.onNodeWithTag("channel-detail-channel").performScrollToNode(hasTestTag("channel-video-search"))
        rule.onNodeWithTag("channel-video-search").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("browse-top-bar").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("channel-search-header").performTouchInput { swipeDown() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("browse-top-bar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("channel-detail-channel").assertIsDisplayed()
    }

    @Test fun advancedOptionsNeedTenVersionTapsAndJobsIconIsHiddenWhenIdle() {
        val player = startPlayer()
        val isolated = object : ContextWrapper(rule.activity) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(preferenceName + name, mode)
        }
        rule.setContent { CompositionLocalProvider(LocalContext provides isolated) {
            GrayjayTheme(dynamicColor = false) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) }
        } }
        rule.onNodeWithTag("active-jobs-toggle").assertDoesNotExist()
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("version-information"))
        repeat(9) { rule.onNodeWithTag("version-information").performClick() }
        assertFalse(AdvancedOptions.isUnlocked(isolated))
        rule.onNodeWithTag("version-information").performClick()
        assertTrue(AdvancedOptions.isUnlocked(isolated))
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("advanced-settings"))
        rule.onNodeWithTag("advanced-settings").performClick()
        rule.onNodeWithTag("backend-change-notices").assertIsOff().performClick().assertIsOn()
        assertTrue(AdvancedOptions.showBackendChanges(isolated))
    }

    @Test fun skippingCrashPromptRequiresConfirmationAndCanBeCancelled() {
        var shown by mutableStateOf(true)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            if (shown) PreviousCrashDialog("fixture", true) { shown = false }
        } }
        val skip = rule.activity.getString(R.string.skip_crash_report_action)
        rule.onNodeWithTag("previous-crash-prompt").assertIsDisplayed()
        capture("previous-crash-prompt")
        rule.onNodeWithText(skip).performClick()
        rule.onNodeWithTag("skip-crash-confirmation").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.back)).performClick()
        rule.onNodeWithTag("previous-crash-prompt").assertIsDisplayed()
        rule.onNodeWithText(skip).performClick()
        rule.onNodeWithText(skip).performClick()
        rule.runOnIdle { assertFalse(shown) }
    }

    private fun capture(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }; bitmap.recycle()
    }
}
