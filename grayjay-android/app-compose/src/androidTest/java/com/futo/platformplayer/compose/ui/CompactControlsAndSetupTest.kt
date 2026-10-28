package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.DevicePerformanceProfile
import com.futo.platformplayer.compose.LocalDevicePerformanceProfile
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactControlsAndSetupTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun smallerControlsKeepTouchTargetsAndSwitchSemantics() {
        var low by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var toggles = 0
        var plays = 0
        var chipClicks = 0
        rule.setContent { CompositionLocalProvider(LocalDevicePerformanceProfile provides DevicePerformanceProfile(low, low, !low)) {
            GrayjayTheme(dynamicColor = false) { Column(Modifier.systemBarsPadding()) {
                AdaptiveFilterChip(true, { chipClicks++ }, { Text("Playlist") }, Modifier.testTag("tab"))
                AdaptiveSwitch(enabled, { enabled = it; toggles++ }, Modifier.testTag("switch"))
                AdaptiveButton({ plays++ }, Modifier.testTag("button")) { Text("Segui") }
            } }
        } }
        val wide = rule.onNodeWithTag("tab").fetchSemanticsNode().boundsInRoot.width
        rule.runOnIdle { low = true }
        val compact = rule.onNodeWithTag("tab").fetchSemanticsNode().boundsInRoot
        assertTrue(compact.width < wide)
        val density = rule.activity.resources.displayMetrics.density
        // The visible capsule is smaller; Foundation extends its hit target into the reserved padding.
        rule.onNodeWithTag("tab").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, -6 * density)) }
        assertEquals(1, chipClicks)
        assertTrue(rule.onNodeWithTag("switch").fetchSemanticsNode().boundsInRoot.height >= 48 * density - 1)
        rule.onNodeWithTag("tab").assertIsSelected()
        rule.onNodeWithTag("switch").assertIsOn().performClick().assertIsOff()
        assertEquals(1, toggles)
        rule.onNodeWithTag("button").performClick()
        assertEquals(1, plays)
        capture("compact-controls")
    }

    @Test fun sourceHeadingHasActiveCountAndAddActionWithoutTheBanner() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val state = GrayjayUiState(sources = listOf(
            SourceUiModel("youtube", "YouTube", "Video", 0L, true),
            SourceUiModel("spotify", "Spotify", "Music", 0L, false)))
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, GrayjayAppActions()) } }
            rule.onNodeWithTag("nav-settings").performClick()
            rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("manage-sources"))
            rule.onNodeWithTag("manage-sources").performClick()
            rule.onNodeWithTag("source-active-count").assertTextEquals(rule.activity.resources.getQuantityString(R.plurals.active_sources_short, 1, 1))
            rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.active_sources, 1, 1)).assertDoesNotExist()
            rule.onNodeWithTag("add-source").assertIsDisplayed().assertHasClickAction()
            capture("sources-without-banner")
        } finally { rule.runOnUiThread { player.release() } }
    }

    @Test fun diagnosticsIsAnEnabledOptionalWizardStep() {
        var state by mutableStateOf(GrayjayUiState())
        val actions = GrayjayAppActions().apply { onCrashLoggingChange = { state = state.copy(crashLoggingEnabled = it) } }
        val page = guidedSetupSteps(false, false).indexOf(SetupStep.Diagnostics)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(state, actions, page, {}, {}, { it() })
        } }
        rule.onNodeWithTag("setup-page-diagnostics").assertIsDisplayed()
        capture("setup-diagnostics-default")
        rule.onAllNodes(isToggleable()).onFirst().assertIsOn().performClick().assertIsOff()
        assertFalse(state.crashLoggingEnabled)
    }

    @Test fun enablingSharedTransitionsRetainsTheExistingScreenState() {
        var compositions = 0
        var disposed = 0
        var value by mutableIntStateOf(0)
        lateinit var controller: CreatorSheetController
        val channel = ChannelUiModel("creator", "Creator", "youtube", "YouTube", 0, "", "")
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CreatorSheetTransitionHost("main", {}, channel, false) {
                controller = checkNotNull(LocalCreatorSheet.current)
                val remembered = remember { mutableStateOf("kept") }
                DisposableEffect(Unit) { compositions++; onDispose { disposed++ } }
                Text("${remembered.value}:$value", Modifier.testTag("retained-state"))
            }
        } }
        rule.runOnIdle { value = 7; controller.show(channel, false, {}, {}) }
        rule.onNodeWithTag("retained-state").assertTextEquals("kept:7")
        rule.runOnIdle { controller.dismiss() }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("retained-state").assertTextEquals("kept:7")
        assertEquals(1, compositions)
        assertEquals(0, disposed)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(400)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
