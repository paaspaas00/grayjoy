package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.ActiveJobsButton
import com.futo.platformplayer.compose.ui.screens.ActiveJobsPanel
import com.futo.platformplayer.compose.ui.screens.rememberActiveJobItems
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel

class ActiveJobsHeaderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun mediaExportHasProgressAndItsOwnCancellation() {
        var cancelled: String? = null
        composeRule.setContent {
            GrayjayTheme(dynamicColor = false, darkTheme = false) {
                val jobs = rememberActiveJobItems(
                    youtubeImport = YoutubeImportUiState(),
                    backgroundYoutubeImport = BackgroundYoutubeImportUiState(),
                    databaseImport = DatabaseImportUiState(),
                    downloads = emptyMap(), videos = emptyList(),
                    sourceOperationInProgress = false, sourceOperationMessage = null,
                    updateDownload = null, libraryTransfer = LibraryTransferUiState(),
                    mediaExports = listOf(MediaExportUiState(
                        id = "fixture", mediaType = DownloadMediaType.Audio, completed = 1,
                        total = 4, currentTitle = "Export fixture", progress = 0.4f,
                    )),
                )
                ActiveJobsPanel(
                    visible = true, jobs = jobs,
                    onCancelYoutubeImports = { error("Wrong cancellation target") },
                    onCancelDownloads = { error("Must preserve original downloads") },
                    onOpenJob = {},
                    onCancelMediaExport = { cancelled = it },
                )
            }
        }
        composeRule.onNodeWithTag("active-job-card-media-export:fixture").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cancel),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.cancel_media_export_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.cancel)).performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals("fixture", cancelled) }
    }

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
                        onCancelDownloads = {},
                        onOpenJob = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(importTitle).assertIsDisplayed()
        composeRule.onNodeWithText(progressLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(cancel).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.cancel_youtube_import_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.cancel_import_action),
        ).performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun downloadCancellationRequiresConfirmation() {
        var cancelled = false
        var opened = false
        composeRule.setContent {
            GrayjayTheme(dynamicColor = false, darkTheme = false) {
                val jobs = rememberActiveJobItems(
                    youtubeImport = YoutubeImportUiState(),
                    backgroundYoutubeImport = BackgroundYoutubeImportUiState(),
                    databaseImport = DatabaseImportUiState(),
                    downloads = mapOf(
                        "video" to DownloadUiModel(
                            profileId = "main",
                            videoId = "video",
                            mediaType = DownloadMediaType.Video,
                            status = DownloadStatus.Queued,
                            activeMediaTypes = setOf(DownloadMediaType.Video),
                        ),
                    ),
                    videos = emptyList(),
                    sourceOperationInProgress = false,
                    sourceOperationMessage = null,
                    updateDownload = null,
                    libraryTransfer = LibraryTransferUiState(),
                )
                ActiveJobsPanel(
                    visible = true,
                    jobs = jobs,
                    onCancelYoutubeImports = {},
                    onCancelDownloads = { cancelled = true },
                    onOpenJob = { opened = it == "downloads" },
                )
            }
        }
        composeRule.onNodeWithTag("active-job-card-downloads").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cancel),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.cancel_active_downloads_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.cancel_downloads_action),
        ).performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun lowStorageWarningIsVisibleInButtonAndPanel() {
        val storage = DownloadStorageUiState(
            availableBytes = 128L * 1024L * 1024L,
            requiredFreeBytes = 512L * 1024L * 1024L,
            isWarning = true,
            downloadsPaused = true,
        )
        composeRule.setContent {
            GrayjayTheme(dynamicColor = false, darkTheme = false) {
                Column {
                    ActiveJobsButton(
                        jobs = emptyList(),
                        expanded = true,
                        storageWarning = storage,
                        onClick = {},
                    )
                    ActiveJobsPanel(
                        visible = true,
                        jobs = emptyList(),
                        onCancelYoutubeImports = {},
                        onCancelDownloads = {},
                        onOpenJob = {},
                        storageWarning = storage,
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.storage_almost_full),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("active-jobs-storage-warning").assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.storage_almost_full),
        ).assertIsDisplayed()
    }
}
