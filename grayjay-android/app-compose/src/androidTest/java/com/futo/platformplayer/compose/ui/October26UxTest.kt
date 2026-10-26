package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class October26UxTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    @After fun release() { rule.runOnUiThread { player?.release() } }
    private fun player(): ExoPlayer {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity).build() }
        return player!!
    }

    @Test fun downloadedMusicFiltersAndLibraryCountsReplaceSummaryBanners() {
        val player = player()
        val videos = listOf(
            VideoUiModel("music", "Music", "Artist", "", "3:00", isMusic = true, isDownloaded = true, isWatchLater = true),
            VideoUiModel("speech", "Speech", "Creator", "", "3:00", isMusic = false, isDownloaded = true),
            VideoUiModel("unknown", "Unknown", "Creator", "", "3:00", isDownloaded = true))
        val playlists = listOf(PlaylistUiModel("p1", "First", "", listOf("music")), PlaylistUiModel("p2", "Second", "", listOf("speech")))
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(
            GrayjayUiState(videos = videos, libraryVideos = videos, playlists = playlists), player, GrayjayAppActions()) } }
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("library-filter-playlists").performScrollTo().performClick()
        rule.onNodeWithTag("library-count-playlists").assertTextEquals("2")
        rule.onNodeWithTag("library-summary-playlists").assertDoesNotExist()
        val playlistTab = rule.onNodeWithTag("library-filter-playlists").fetchSemanticsNode().boundsInRoot
        val laterTab = rule.onNodeWithTag("library-filter-watchlater").fetchSemanticsNode().boundsInRoot
        assertTrue(playlistTab.left < laterTab.left)
        rule.onNodeWithTag("library-filter-watchlater").performScrollTo().performClick()
        rule.onNodeWithTag("library-count-watchlater").assertTextEquals("1")
        rule.onNodeWithTag("library-summary-watchlater").assertDoesNotExist()
        rule.onNodeWithTag("library-filter-downloads").performScrollTo().performClick()
        rule.onNodeWithTag("library-count-downloads").assertTextEquals("3")
        rule.onNodeWithTag("library-summary-downloads").assertDoesNotExist()
        rule.onNodeWithTag("downloads-music-music").performClick()
        rule.onNodeWithTag("library-count-downloads").assertTextEquals("1")
        rule.onNodeWithTag("video-card-music").assertIsDisplayed()
        rule.onNodeWithTag("video-card-speech").assertDoesNotExist()
        rule.onNodeWithTag("downloads-music-nonmusic").performClick()
        rule.onNodeWithTag("video-card-speech").assertIsDisplayed()
        rule.onNodeWithTag("video-card-unknown").assertDoesNotExist()
        rule.onNodeWithTag("downloads-music-all").performClick()
        capture("oct26-download-filters")
    }

    @Test fun temporaryQueueOnlySavesAfterExplicitNamedConfirmation() {
        val queue = PlaylistUiModel("temporary", "In coda", "", listOf("a", "b", "c"), isPlaybackQueue = true)
        var saved: Pair<String, List<String>>? = null
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalTemporaryQueue provides TemporaryQueueActions(queue, listOf("Existing"), { name, ids -> saved = name to ids })) {
                QueueSectionHeader()
            }
        } }
        assertNull(saved)
        rule.onNodeWithTag("save-playback-queue").performClick()
        rule.onNodeWithTag("confirm-save-queue").assertIsNotEnabled()
        rule.onNodeWithTag("save-queue-name").performTextInput("Existing")
        rule.onNodeWithTag("confirm-save-queue").assertIsNotEnabled()
        rule.onNodeWithTag("save-queue-name").performTextReplacement("My queue")
        rule.onNodeWithTag("confirm-save-queue").performClick()
        assertEquals("My queue" to listOf("a", "b", "c"), saved)
        rule.onNodeWithTag("save-queue-name").assertDoesNotExist()
    }

    @Test fun channelPreviewSwipesIntoChannelPageAndDisplaysLoadingSkeletons() {
        val player = player()
        val channel = ChannelUiModel("channel-test", "Channel example", "youtube", "YouTube", 0, "10K", "A channel description for the animated preview.")
        val video = VideoUiModel("preview-video", "Video example", channel.name, "", "3:00", channelId = channel.id, authorUrl = channel.id,
            sourceId = "youtube", description = "Description")
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video), channels = listOf(channel),
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id), durationMs = 180_000),
            nowPlaying = NowPlayingUiState(video = video),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onLoadChannel = { state = state.copy(channelDetail = ChannelDetailUiState(channelId = it.id, channel = it, isLoading = true)) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("now-playing-creator").performScrollTo().performClick()
        rule.onNodeWithTag("creator-transition-sheet").assertIsDisplayed()
        capture("oct26-creator-preview")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("creator-transition-sheet").performTouchInput {
            swipe(Offset(width * .4f, height * .55f), Offset(width * .4f, -150f), 500)
        }
        rule.mainClock.advanceTimeBy(112)
        capture("oct26-creator-morph-middle")
        rule.mainClock.advanceTimeBy(500)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("creator-sheet-overlay").assertDoesNotExist()
        rule.onNodeWithTag("channel-detail-${channel.id}").assertIsDisplayed()
        capture("oct26-channel-from-preview")
        rule.onNodeWithTag("channel-loading").performScrollTo().assertIsDisplayed()
    }

    @Test fun unsavedQueueRemainsVisibleWithoutItsSearchResultsAndDisappearsOnClose() {
        val player = player()
        val videos = List(3) { VideoUiModel("queued-$it", "Queued video $it", "Creator", "", "3:00") }
        val queue = PlaylistUiModel("temporary-queue", "In coda", "", videos.map { it.id }, isPlaybackQueue = true)
        var played: String? = null
        var state by mutableStateOf(GrayjayUiState(temporaryPlaybackQueue = queue, playbackQueueVideos = videos,
            playback = PlaybackUiState(currentVideoId = videos.first().id, fullQueueVideoIds = queue.videoIds, queueVideoIds = listOf(videos.first().id)),
            nowPlaying = NowPlayingUiState(video = videos.first()),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, videos.first().id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onPlayQueuedVideo = { played = it }
            onClosePlayback = { state = state.copy(playback = PlaybackUiState(), nowPlaying = NowPlayingUiState(),
                temporaryPlaybackQueue = null, playbackQueueVideos = emptyList()) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("save-playback-queue").performScrollTo().assertIsDisplayed()
        capture("oct26-temporary-queue")
        assertTrue(state.playlists.isEmpty())
        rule.onNodeWithTag("video-card-queued-1").performScrollTo().performClick()
        assertEquals("queued-1", played)
        rule.onNodeWithTag("now-playing-close").performClick()
        rule.onNodeWithTag("mini-player").assertDoesNotExist()
        rule.onNodeWithTag("save-playback-queue").assertDoesNotExist()
        assertTrue(state.playlists.isEmpty())
    }

    @Test fun followedChannelSelectionBarIsAboveNavigationInListAndGrid() {
        val player = player()
        val channels = List(12) { ChannelUiModel("creator-$it", "Creator $it", "youtube", "YouTube", 0, "", "") }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(
            GrayjayUiState(channels = channels, followedCreatorIds = channels.mapTo(mutableSetOf()) { it.id }), player, GrayjayAppActions()) } }
        rule.onNodeWithTag("nav-subscriptions").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.manage)).performClick()
        rule.onNodeWithTag("manage-subscription-creator-0").performTouchInput { longClick() }
        fun assertBottom() {
            val bar = rule.onNodeWithTag("manage-subscriptions-selection-header").fetchSemanticsNode().boundsInRoot
            val nav = rule.onNodeWithTag("nav-library").fetchSemanticsNode().boundsInRoot
            val filters = rule.onNodeWithTag("manage-subscriptions-source-filter").fetchSemanticsNode().boundsInRoot
            assertTrue(bar.top > filters.bottom + 100f)
            assertTrue(bar.bottom <= nav.bottom && nav.top - bar.bottom < 50f)
        }
        assertBottom()
        rule.onNodeWithTag("manage-subscriptions-layout").performClick()
        assertBottom()
        capture("oct26-manage-subscriptions-selection")
        rule.onNodeWithTag("remove-selected-subscriptions").performClick()
        rule.onNodeWithTag("confirm-remove-subscriptions").assertIsDisplayed()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
