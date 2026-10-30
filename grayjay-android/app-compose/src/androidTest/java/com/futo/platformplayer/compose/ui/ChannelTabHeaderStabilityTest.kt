package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import com.futo.platformplayer.compose.R
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ChannelTabHeaderStabilityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val channel = ChannelUiModel("tab-layout", "A channel with several tabs", "youtube", "YouTube", 0, "6M followers",
        "A channel description with enough text to verify a partially collapsed summary.\nSome more channel information and another line.")
    private val videos = (1..40).map { VideoUiModel("long-$it", "Video $it", "Creator", "", "3:00") }
    private var detail by mutableStateOf(ChannelDetailUiState(channelId = channel.id, channel = channel,
        videos = videos, shorts = videos.take(2), liveStreams = listOf(videos.first().copy(id = "live-one")),
        supportsShorts = true, supportsPlaylists = true, liveContentType = "streams", isLoaded = true))
    private var pinned = false
    @Before fun setup() { rule.runOnUiThread {
        rule.activity.enableEdgeToEdge()
        rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    } }
    private fun show() {
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                ChannelDetailScreen(channel, detail, false, {}, onTabSelected = { detail = detail.copy(selectedTab = it) },
                    onPlaylistClick = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {},
                    onSearchModeChange = { pinned = it })
            }
        } }
    }
    private fun headerTop() = rule.onNodeWithTag("channel-search-header").fetchSemanticsNode().boundsInRoot.top
    private fun select(tab: ChannelContentTab, expectedTop: Float) {
        rule.onNodeWithTag("channel-tab-${tab.name.lowercase()}").performClick()
        assertEquals("Header moved when selecting $tab", expectedTop, headerTop(), 2f)
    }

    @Test fun shortEmptyAndLoadingTabsPreserveAPartiallyCollapsedHeader() {
        show()
        val list = rule.onNodeWithTag("channel-detail-${channel.id}")
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val distance = (headerTop() - listTop) * .4f
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        val top = headerTop()
        assertTrue("Fixture must leave a partially visible summary", top > listTop + 10f)
        capture("channel-tabs-partial-before")
        select(ChannelContentTab.Live, top)
        capture("channel-tabs-partial-live")
        rule.runOnIdle { detail = detail.copy(isLoading = true) }
        assertEquals(top, headerTop(), 2f)
        rule.runOnIdle { detail = detail.copy(isLoading = false) }
        assertEquals(top, headerTop(), 2f)
        select(ChannelContentTab.Shorts, top)
        select(ChannelContentTab.Playlists, top)
        capture("channel-tabs-partial-empty")
        select(ChannelContentTab.Videos, top)
        rule.onNodeWithTag("channel-results-pane").assertDoesNotExist()
    }

    @Test fun switchingAwayFromDeepResultsKeepsTheHeaderPinnedAndAvoidsBlankOverscroll() {
        show()
        val list = rule.onNodeWithTag("channel-detail-${channel.id}")
        list.performScrollToIndex(15)
        val top = headerTop()
        select(ChannelContentTab.Live, top)
        rule.onNodeWithTag("video-card-live-one").assertIsDisplayed()
        val rowTop = rule.onNodeWithTag("video-card-live-one").fetchSemanticsNode().boundsInRoot.top
        list.performTouchInput { swipeUp() }
        assertEquals("A short tab must not scroll into a blank footer", rowTop,
            rule.onNodeWithTag("video-card-live-one").fetchSemanticsNode().boundsInRoot.top, 2f)
        select(ChannelContentTab.Playlists, top)
        select(ChannelContentTab.Videos, top)
        list.performTouchInput { swipeDown() }
        assertTrue("The channel summary must still be reachable", headerTop() > top + 50f)
        capture("channel-tabs-summary-reachable")
    }

    @Test fun playlistTabDoesNotExpandAPinnedSearchHeader() {
        show()
        rule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasTestTag("channel-video-search"))
        rule.onNodeWithTag("channel-video-search").performClick()
        rule.waitUntil(5000) { pinned }
        val top = headerTop()
        select(ChannelContentTab.Live, top)
        select(ChannelContentTab.Playlists, top)
        rule.runOnIdle { assertTrue(pinned) }
        rule.onNodeWithTag("channel-header-avatar").assertDoesNotExist()
        rule.onNodeWithTag("channel-search-header").performTouchInput { swipeDown() }
        rule.runOnIdle { assertFalse(pinned) }
        rule.onNodeWithTag("channel-header-avatar").assertExists()
    }

    @Test fun resultsHaveNoCountAndTheHeadingSharesTheSortRow() {
        detail = detail.copy(videos = videos.take(30), hasMore = true)
        show()
        val list = rule.onNodeWithTag("channel-detail-${channel.id}")
        list.performScrollToNode(hasTestTag("channel-video-search"))
        val compact = rule.onAllNodesWithTag("channel-result-heading").fetchSemanticsNodes().isEmpty()
        fun checkHeading() {
            if (!compact) {
                val heading = rule.onNodeWithTag("channel-result-heading")
                heading.assertTextEquals(rule.activity.getString(detail.selectedTab.labelRes))
                val headingBounds = heading.fetchSemanticsNode().boundsInRoot
                val sort = rule.onNodeWithTag("channel-sort").fetchSemanticsNode().boundsInRoot
                assertEquals("The section title must share the sort row", sort.center.y, headingBounds.center.y, 2f)
                assertTrue("The title must not overlap the sort control", headingBounds.right <= sort.left + 1f)
            }
            for (number in listOf("30+", "40+", "30", "40", "3+", "3")) {
                rule.onNodeWithText(number, useUnmergedTree = true).assertDoesNotExist()
                rule.onNodeWithText("${rule.activity.getString(R.string.videos)} ($number)").assertDoesNotExist()
            }
        }
        checkHeading()
        capture("channel-heading-next-to-sort")
        rule.runOnIdle { detail = detail.copy(videos = videos) }
        checkHeading()
        rule.runOnIdle { detail = detail.copy(hasMore = false) }
        checkHeading()
        rule.runOnIdle { detail = detail.copy(searchQuery = "Video", searchIsRemote = true,
            searchVideos = videos.take(3), searchHasMore = true) }
        checkHeading()
        rule.runOnIdle { detail = detail.copy(searchHasMore = false) }
        checkHeading()
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
