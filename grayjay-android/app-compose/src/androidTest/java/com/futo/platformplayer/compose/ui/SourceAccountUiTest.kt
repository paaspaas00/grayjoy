package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.SourcesScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceAccountUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun sourceSheetShowsIdentityUpdatesAndClearsItOnLogout() {
        var source by mutableStateOf(SourceUiModel("youtube", "YouTube", "", 0xFFE53935L,
            true, isAuthenticated = true, accountName = "Example channel"))
        var refreshes = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            SourcesScreen(listOf(source), false, null, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                YoutubeImportUiState(), BackgroundYoutubeImportUiState(), YoutubeImportScheduleUiState(),
                { _, _ -> }, {}, {}, { _, _, _ -> }, onRefreshSourceAccount = { refreshes++ })
        } }
        rule.onNodeWithTag("source-options-youtube").performClick()
        rule.onNodeWithTag("source-account-name")
            .assertTextEquals(rule.activity.getString(R.string.source_logged_in_as, "Example channel"))
        rule.waitForIdle()
        Thread.sleep(400)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(rule.activity.cacheDir, "source-account-options.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        } } finally { screenshot.recycle() }
        rule.runOnIdle { assertEquals(1, refreshes); source = source.copy(accountName = "New account") }
        rule.onNodeWithTag("source-account-name")
            .assertTextEquals(rule.activity.getString(R.string.source_logged_in_as, "New account"))
        rule.runOnIdle { source = source.copy(isAuthenticated = false, accountName = null) }
        rule.onNodeWithTag("source-account-name").assertDoesNotExist()
    }
}
