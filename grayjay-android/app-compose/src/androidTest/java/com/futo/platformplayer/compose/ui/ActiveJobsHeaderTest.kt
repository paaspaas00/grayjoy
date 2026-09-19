package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.ActiveJobsButton
import com.futo.platformplayer.compose.ui.screens.ActiveJobsPanel
import com.futo.platformplayer.compose.ui.screens.rememberActiveJobItems
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActiveJobsHeaderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun youtubeImportProgressAndCancellationAreAvailableFromHeader() {
        var cancelled = false
        val importTitle = composeRule.activity.getString(R.string.import_from_youtube)
        val stage = composeRule.activity.getString(R.string.youtube_importing_subscriptions)
        val progressLabel = composeRule.activity.getString(
            R.string.youtube_import_progress_count,
            stage,
            2,
            4,
        )
        val cancel = composeRule.activity.getString(R.string.cancel)
        composeRule.setContent {
            GrayjayTheme(dynamicColor = false, darkTheme = false) {
                val jobs = rememberActiveJobItems(
                    youtubeImport = YoutubeImportUiState(
                        isRunning = true,
                        stage = YoutubeImportStageUi.Subscriptions,
                        completed = 2,
                        total = 4,
                    ),
                    backgroundYoutubeImport = BackgroundYoutubeImportUiState(),
                    databaseImport = DatabaseImportUiState(),
                    downloads = emptyMap(),
                    videos = emptyList(),
                    sourceOperationInProgress = false,
                    sourceOperationMessage = null,
                    updateDownload = null,
                    libraryTransfer = LibraryTransferUiState(),
                )
                Column {
                    ActiveJobsButton(jobs, expanded = true, onClick = {})
                    ActiveJobsPanel(
                        visible = true,
                        jobs = jobs,
                        onCancelYoutubeImports = { cancelled = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText(importTitle).assertIsDisplayed()
        composeRule.onNodeWithText(progressLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(cancel).performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }
}
