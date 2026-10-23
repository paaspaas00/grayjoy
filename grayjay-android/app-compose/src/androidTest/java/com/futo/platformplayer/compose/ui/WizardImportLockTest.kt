package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.GuidedSetupDialog
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WizardImportLockTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun importShowsSharedProgressAndLocksButtonsGesturesUntilCompleted() {
        var state by mutableStateOf(GrayjayUiState(youtubeImport = YoutubeImportUiState(
            isRunning = true, stage = YoutubeImportStageUi.Playlists, completed = 1, total = 4, currentItemCompleted = 17)))
        var closed = false
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(state, GrayjayAppActions(), 0, {}, { closed = true }, { it() })
        } }
        rule.onNodeWithTag("setup-page-import-details").assertIsDisplayed()
        rule.onNodeWithTag("setup-back").assertIsNotEnabled()
        rule.onNodeWithTag("setup-next").assertIsNotEnabled()
        rule.onNodeWithTag("youtube-import-progress").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.setup_never_again)).assertDoesNotExist()
        rule.onNodeWithTag("setup-page-import-details").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("setup-page-import-details").assertIsDisplayed()
        rule.onNodeWithTag("setup-page-import-details").performTouchInput { swipeRight() }
        rule.onNodeWithTag("setup-page-import-details").assertIsDisplayed()
        rule.runOnIdle { assertFalse(closed); state = state.copy(youtubeImport = state.youtubeImport.copy(isRunning = false)) }
        rule.onNodeWithTag("setup-next").assertIsEnabled().performClick()
        rule.onNodeWithTag("setup-page-shorts").assertIsDisplayed()
    }
}
