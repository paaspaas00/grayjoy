package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.futo.platformplayer.compose.ui.screens.LibraryFilter
import com.futo.platformplayer.compose.ui.screens.LibraryScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryJobNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun jobRequestOverridesExistingHistoryPageAndReachesActiveItem() {
        val filter = mutableStateOf(LibraryFilter.History)
        val target = mutableStateOf<String?>(null)
        val videos = (1..30).map {
            VideoUiModel("video-$it", "Video $it", "Creator", "", "1:00",
                isDownloaded = true, lastWatchedAt = it.toLong())
        }
        rule.setContent {
            GrayjayTheme(dynamicColor = false) {
                LibraryScreen(
                    videos = videos,
                    playlists = emptyList(),
                    onVideoClick = {},
                    onVideoLongClick = {},
                    onPlaylistClick = {},
                    onAddSelectionToPlaylist = {},
                    onQueueSelection = {},
                    onRemoveSelectionFromHistory = {},
                    selectedFilter = filter.value,
                    onSelectedFilterChange = { filter.value = it },
                    downloadFocusVideoId = target.value,
                    onDownloadFocusConsumed = { target.value = null },
                )
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            target.value = "video-27"
            filter.value = LibraryFilter.Downloads
        }
        rule.onNodeWithTag("video-card-video-27").assertIsDisplayed()
        rule.runOnIdle { assertEquals(LibraryFilter.Downloads, filter.value) }
    }
}
