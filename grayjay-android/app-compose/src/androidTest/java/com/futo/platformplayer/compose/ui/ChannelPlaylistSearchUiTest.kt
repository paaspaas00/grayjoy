package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ChannelPlaylistSearchUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val channel = ChannelUiModel("playlist-search", "A channel", "youtube", "YouTube", 0, "", "Description")
    private var detail by mutableStateOf(ChannelDetailUiState(channelId = channel.id, channel = channel,
        selectedTab = ChannelContentTab.Playlists, supportsPlaylists = true, supportsReleases = true, isLoaded = true,
        playlists = listOf(PlaylistUiModel("circuits", "Hardware", "Circuits and electronics", emptyList()),
            PlaylistUiModel("travel", "Travel", "Journeys", emptyList()))))
    private var pinned = false
    private var paging = 0
    @Before fun setup() = rule.runOnUiThread {
        rule.activity.enableEdgeToEdge()
        rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
    private fun show(onQuery: (String) -> Unit = { detail = detail.copy(searchQuery = it) }, onPage: () -> Unit = {}) {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                ChannelDetailScreen(channel, detail, false, {}, onTabSelected = { detail = detail.copy(selectedTab = it, searchQuery = "") },
                    onPlaylistClick = {}, onLoadMore = onPage, onVideoClick = {}, onVideoLongClick = {},
                    onSearchQueryChange = onQuery, onLoadMoreSearch = { error("Playlist queries must not use video search paging") },
                    onSearchModeChange = { pinned = it })
            }
        } }
    }
    private fun search() = rule.onNodeWithTag("channel-video-search").also {
        rule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasTestTag("channel-video-search"))
    }
    @Test fun playlistsUseTheSamePinnedSearchAndFilterTheirTitlesAndDescriptions() {
        show()
        search().performClick().performTextInput("CIRCUITS")
        rule.waitUntil(5000) { pinned }
        rule.onNodeWithText("Hardware").assertIsDisplayed()
        rule.onNodeWithText("Travel").assertDoesNotExist()
        rule.onNodeWithTag("channel-header-avatar").assertDoesNotExist()
        capture("channel-playlist-search-pinned")
        search().performImeAction()
        rule.runOnIdle { assertTrue(pinned) }
        rule.onNodeWithTag("channel-search-header").performTouchInput { swipeDown() }
        rule.runOnIdle { assertFalse(pinned) }
        rule.onNodeWithTag("channel-header-avatar").assertExists()
    }
    @Test fun aSearchCanFindAPlaylistOnLaterPagesAndRetainsTheLoadedPagesAfterClearing() {
        show(onQuery = { detail = detail.copy(searchQuery = it, hasMore = it.isNotBlank(), continuationId = "next") },
            onPage = {
                paging++
                val page = paging
                detail = detail.copy(isLoadingMore = true)
                rule.activity.lifecycleScope.launch {
                    delay(30)
                    detail = detail.copy(playlists = detail.playlists + PlaylistUiModel("page-$page",
                        if (page == 2) "Needle collection" else "Unrelated collection", "", emptyList()),
                        isLoadingMore = false, hasMore = page < 2, continuationId = if (page < 2) "next" else null)
                }
            })
        search().performTextInput("needle")
        rule.waitUntil(5000) { paging == 2 && !detail.isLoadingMore }
        rule.onNodeWithText("Needle collection").assertIsDisplayed()
        rule.onNodeWithText("Travel").assertDoesNotExist()
        search().performTextClearance()
        rule.onNodeWithText("Hardware").assertExists()
        rule.runOnIdle { assertTrue(detail.playlists.any { it.id == "page-2" }) }
        capture("channel-playlist-search-later-page")
    }
    @Test fun aPagingFailureDoesNotContinuouslyRetryAnEmptySearch() {
        detail = detail.copy(searchQuery = "missing", hasMore = true, continuationId = "next", errorMessage = "Connection lost")
        show(onPage = { paging++ })
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(0, paging) }
        rule.onNodeWithText("Connection lost").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.retry)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, paging) }
    }
    @Test fun cachedPagesAdvanceEvenWhenLoadingStartsAndEndsWithinTheSameFrame() {
        show(onQuery = { detail = detail.copy(searchQuery = it, hasMore = true, continuationId = "cached") },
            onPage = {
                paging++
                detail = detail.copy(isLoadingMore = true)
                detail = detail.copy(isLoadingMore = false, hasMore = paging < 3,
                    playlists = detail.playlists + PlaylistUiModel("cached-$paging",
                        if (paging == 3) "Needle cached" else "Other $paging", "", emptyList()))
            })
        search().performTextInput("needle")
        rule.waitUntil(10_000) { paging == 3 }
        rule.onNodeWithText("Needle cached").assertIsDisplayed()
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
