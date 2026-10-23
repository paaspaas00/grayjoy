package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceProfilesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val sources = listOf("youtube", "spotify", "nebula", "twitch", "crunchyroll").map {
        SourceUiModel(it, it.replaceFirstChar(Char::uppercase), "", 0xFF336699, it == "youtube")
    } + (1..20).map { SourceUiModel("extra-$it", "Extra $it", "", 0xFF336699, false) }
    @Test fun wizardDoesNothingWithoutConsentAndCreatesOnlySelectedSourceProfiles() {
        var state by mutableStateOf(GrayjayUiState(sources = sources, sourceProfileCatalog = sources))
        var created: Set<String>? = null
        val actions = GrayjayAppActions().apply { onConfigureSourceProfiles = { ids ->
            created = ids
            state = state.copy(sourceProfilesEnabled = true, profiles = ids.map {
                ProfileUiModel(it, it, ProfileProtection.None, sourceId = it)
            })
        } }
        val index = guidedSetupSteps(false, false).indexOf(SetupStep.SourceProfiles)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(state, actions, index, {}, {}, { it() })
        } }
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-page-help").assertIsDisplayed()
        rule.runOnIdle { assertNull(created) }
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.profile_split_yes)).performScrollTo().performClick()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-page-extra-source-profiles").assertIsDisplayed()
        rule.onNodeWithTag("setup-create-source-profiles").assertIsDisplayed()
        screenshot("source-profile-options")
        rule.onNodeWithText("Twitch").performScrollTo().performClick()
        rule.onNodeWithTag("setup-create-source-profiles").performClick()
        rule.runOnIdle { assertEquals(setOf("youtube", "spotify", "twitch"), created) }
        rule.onNodeWithTag("setup-next").assertIsEnabled().performClick()
        rule.onNodeWithTag("setup-page-help").assertIsDisplayed()
    }
    @Test fun headerSwipeSkipsPinAndBiometricProfilesAndTapStillOpensPicker() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val profiles = listOf(
            ProfileUiModel("yt", "YouTube", ProfileProtection.None, true, "youtube", sourceAccentColor = 0xFFE53935),
            ProfileUiModel("pin", "Protected", ProfileProtection.Pin),
            ProfileUiModel("sp", "Spotify", ProfileProtection.None, true, "spotify", sourceAccentColor = 0xFF1DB954),
            ProfileUiModel("private", "Private", ProfileProtection.DeviceCredential))
        var state by mutableStateOf(GrayjayUiState(profiles = profiles, activeProfileId = "yt",
            sources = sources, sourceProfilesEnabled = true))
        val changes = mutableListOf<String>()
        val actions = GrayjayAppActions().apply { onSwitchProfile = {
            changes += it
            state = state.copy(activeProfileId = it)
        } }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false,
                sourceAccentColor = state.profiles.first { it.id == state.activeProfileId }.sourceAccentColor) {
                GrayjayApp(state, player, actions)
            } }
            rule.onNodeWithTag("profile-menu").performTouchInput { swipeUp() }
            rule.runOnIdle { assertEquals("sp", state.activeProfileId) }
            rule.onNodeWithTag("profile-menu").performTouchInput { swipeDown() }
            rule.runOnIdle { assertEquals(listOf("sp", "yt"), changes) }
            rule.onNodeWithTag("profile-menu").performClick()
            rule.onNodeWithTag("profile-yt").assertIsDisplayed()
            rule.onNodeWithTag("profile-sp").assertIsDisplayed()
            screenshot("source-profile-picker")
            rule.onNodeWithTag("profile-yt").performClick()
            rule.onNodeWithTag("open-help").performClick()
            rule.onNodeWithTag("help-topics").performScrollToNode(hasTestTag("help-topic-Profiles"))
            rule.onNodeWithTag("help-topic-Profiles").performClick()
            rule.onNodeWithTag("help-topics").performScrollToNode(hasTestTag("help-start-Profiles"))
            rule.onNodeWithTag("help-start-Profiles").performClick()
            rule.onNodeWithTag("profile-menu").performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.help_step, 2, 2)).assertIsDisplayed()
        } finally { rule.runOnUiThread { player.release() } }
    }
    @Test fun spotifyNowPlayingHasNoCastButton() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val video = VideoUiModel("song", "A song", "An artist", "", "3:00", sourceId = "spotify")
        val state = GrayjayUiState(sources = listOf(sources[1].copy(isEnabled = true)),
            search = SearchUiState(query = "song", hasSearched = true, videos = listOf(video)))
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, GrayjayAppActions()) } }
            rule.onNodeWithTag("nav-search").performClick()
            rule.onNodeWithTag("video-card-song").performClick()
            rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
            rule.onNodeWithTag("chromecast-button").assertDoesNotExist()
        } finally { rule.runOnUiThread { player.release() } }
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        Thread.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
