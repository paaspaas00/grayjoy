package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.GuidedSetupDialog
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuidedSetupTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun optionalLoginCanBePassedWithNextOrSwipeWithoutAConfirmation() {
        var loggedIn = false
        val actions = GrayjayAppActions().apply { onLoginSource = { loggedIn = true } }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(GrayjayUiState(sources = listOf(SourceUiModel("youtube", "YouTube", "", 0, true))),
                actions, 3, {}, {}, { it() })
        } }
        rule.onNodeWithTag("setup-page-youtube").assertIsDisplayed()
        rule.onNodeWithTag("setup-skip").assertDoesNotExist()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-page-other-platforms").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_later_title)).assertDoesNotExist()
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithTag("setup-page-youtube").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("setup-page-other-platforms").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_later_title)).assertDoesNotExist()
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_sign_in_youtube)).performClick()
        rule.runOnIdle { assertTrue(loggedIn) }
    }

    @Test fun importsAreOptInAndNoImportHidesTheWholeDetailsPage() {
        var imports = 0
        val actions = GrayjayAppActions().apply { onImportYoutube = { _, _ -> imports++ } }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(GrayjayUiState(sources = listOf(
                SourceUiModel("youtube", "YouTube", "", 0, true, isAuthenticated = true))),
                actions, 5, {}, {}, { it() })
        } }
        rule.onNodeWithTag("setup-page-import-choice").assertIsDisplayed()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-page-shorts").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_import_title)).assertDoesNotExist()
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_import_youtube_choice)).performClick()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-page-import-details").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.import_from_youtube)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.import_grayjay_database)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, imports) }
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithTag("setup-no-import").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_no_import_later)).assertIsDisplayed()
        rule.onNodeWithTag("setup-no-import-confirm").performClick()
        rule.onNodeWithTag("setup-page-shorts").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_later_title)).assertDoesNotExist()
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_import_files_choice)).performClick()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-page-import-details").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.import_from_youtube)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.import_grayjay_database)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.import_newpipe_database)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, imports) }
    }

    @Test fun prefsSearchKeepsTheUsefulRowAndClearingRestoresAllSettings() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
            rule.onNodeWithText(rule.activity.getString(R.string.nav_settings)).performClick()
            rule.onNodeWithTag("settings-search").performTextInput(rule.activity.getString(R.string.remember_subtitles))
            rule.onNode(hasText(rule.activity.getString(R.string.remember_subtitles)) and !hasSetTextAction()).assertIsDisplayed()
            rule.onNodeWithText(rule.activity.getString(R.string.keep_screen_awake)).assertDoesNotExist()
            rule.onNodeWithTag("settings-search").performTextClearance()
            rule.onNodeWithText(rule.activity.getString(R.string.app_language)).assertIsDisplayed()
            rule.onNodeWithTag("settings-search").performTextInput("zzzznotanoption")
            rule.onNodeWithText(rule.activity.getString(R.string.settings_no_matches)).assertIsDisplayed()
        } finally { rule.runOnUiThread { player.release() } }
    }
}
