package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PlayerEntryAndLibraryUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var previousLeft = false
    @Before fun setup() = rule.runOnUiThread {
        previousLeft = InteractionFeatures(rule.activity).leftHanded
        InteractionFeatures(rule.activity).leftHanded = false
        player = ExoPlayer.Builder(rule.activity).build()
    }
    @After fun cleanup() = rule.runOnUiThread { InteractionFeatures(rule.activity).leftHanded = previousLeft; player.release() }
    private fun video(id: String = "entry", title: String = "Ocean song") = VideoUiModel(id, title, "The artist", "", "3:10",
        thumbnailUrl = "android.resource://${InstrumentationRegistry.getInstrumentation().context.packageName}/drawable/audit_earth",
        isDownloaded = true, isWatchLater = true, channelId = "artist")

    @Test fun leftHandedMiniPlayerMirrorsArtworkAndUsesTheRequestedControlOrder() {
        val video = video()
        rule.runOnUiThread { InteractionFeatures(rule.activity).leftHanded = true }
        val state = GrayjayUiState(videos = listOf(video), nowPlaying = NowPlayingUiState(video = video),
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id, "next")))
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, GrayjayAppActions()) } }
        val close = rule.onNodeWithTag("mini-player-close").fetchSemanticsNode().boundsInRoot
        val play = rule.onNodeWithTag("mini-player-toggle").fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithTag("mini-player-next").fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithTag("mini-player-title", true).fetchSemanticsNode().boundsInRoot
        val art = rule.onNodeWithTag("mini-player-artwork", true).fetchSemanticsNode().boundsInRoot
        assertTrue(close.right <= play.left && play.right <= next.left)
        assertTrue(next.right <= title.left && title.right <= art.left)
        rule.onNodeWithTag("mini-player-progress", true).assertIsDisplayed()
        capture("left-miniplayer")
    }

    @Test fun searchHasTheSameHeaderAndDoesNotRequestTheKeyboardOnEntry() {
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) } }
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("browse-top-bar").assertIsDisplayed()
        rule.onNodeWithTag("search-field").assertIsDisplayed().assertIsNotFocused()
        val header = rule.onNodeWithTag("browse-top-bar").fetchSemanticsNode().boundsInRoot
        val search = rule.onNodeWithTag("search-field").fetchSemanticsNode().boundsInRoot
        assertTrue(search.top >= header.bottom)
        capture("search-header")
    }

    @Test fun cardExpandsContinuouslyIntoLoadingNowPlaying() {
        val video = video()
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video), home = HomeUiState(videos = listOf(video))))
        var opens = 0
        val actions = GrayjayAppActions().apply { onOpenVideo = {
            opens++
            state = state.copy(nowPlaying = NowPlayingUiState(video = video, isLoadingPlayback = true, isLoadingExtras = true),
                playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id)))
        } }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, actions) } }
        val source = rule.onNodeWithTag("video-thumbnail-${video.id}", true).fetchSemanticsNode().boundsInRoot
        fun headerHighlights(): Int {
            val pixels = rule.onNodeWithTag("browse-top-bar", true).captureToImage().toPixelMap()
            var count = 0
            // The Home-only help button intentionally disappears as Now Playing opens.
            // Compare the central brand instead of treating that icon change as a black flash.
            for (y in 0 until pixels.height step 3) for (x in pixels.width / 4 until pixels.width * 3 / 4 step 3) if (pixels[x, y].red > .5f) count++
            return count
        }
        val visibleHeader = headerHighlights()
        capture("card-before")
        rule.mainClock.autoAdvance = false
        val click = rule.onNodeWithTag("video-card-${video.id}").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnUiThread { click() }
        var time = 0L
        val widths = mutableListOf<Float>()
        for (frame in listOf(32L, 96L, 160L, 224L, 288L, 400L, 700L)) {
            rule.mainClock.advanceTimeBy(frame - time); time = frame
            rule.waitForIdle()
            capture("card-${frame}")
            if (frame == 32L) assertTrue("The browse screen must remain behind the expanding card, not flash black", headerHighlights() >= visibleHeader * .8f)
            rule.onAllNodesWithTag("card-player-morph-art").fetchSemanticsNodes().firstOrNull()?.let { widths.add(it.boundsInRoot.width) }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
        rule.onNodeWithTag("media-player").assertIsDisplayed()
        val target = rule.onNodeWithTag("media-player").fetchSemanticsNode().boundsInRoot
        assertTrue(widths.any { it > source.width + 5 && it < target.width - 5 })
        assertTrue(widths.zipWithNext().all { (a, b) -> b >= a - 1 })
        rule.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun numberedMusicTrackRowsAlsoMorphTheirArtworkIntoThePlayer() {
        val video = video()
        val playlist = PlaylistUiModel("music-list", "A local playlist", "", listOf(video.id))
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video), libraryVideos = listOf(video), playlists = listOf(playlist),
            sources = listOf(SourceUiModel("spotify", "Music", "", 0L, true)),
            externalNavigation = ExternalNavigationUiModel(1L, ExternalNavigationKind.Playlist, playlist.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onPlayPlaylistFrom = { _, id -> state = state.copy(nowPlaying = NowPlayingUiState(video = video),
                playback = PlaybackUiState(currentVideoId = id, queueVideoIds = listOf(id))) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithText(video.title).performClick()
        rule.onNodeWithTag("music-now-playing").assertIsDisplayed()
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
        capture("music-card-open")
    }

    private fun library(filter: LibraryFilter) {
        val videos = listOf(video(), video("orbit", "Orbit melody"), video("other", "A different song").copy(channelId = "other", creator = "Other artist"))
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LibraryScreen(videos, listOf(PlaylistUiModel("p1", "My songs", "", listOf("entry", "orbit"))),
                onVideoClick = {}, onVideoLongClick = {}, onPlaylistClick = {}, onAddSelectionToPlaylist = {},
                onQueueSelection = {}, onRemoveSelectionFromHistory = {}, selectedFilter = filter)
            }
        } }
    }
    @Test fun downloadsCanSearchAndClearTheQuery() {
        library(LibraryFilter.Downloads)
        rule.onNodeWithTag("downloads-search").performTextInput("Orbit")
        rule.onNodeWithTag("video-card-orbit").assertIsDisplayed()
        rule.onNodeWithTag("video-card-entry").assertDoesNotExist()
        capture("downloads-search")
        rule.onNodeWithTag("downloads-search").performTextClearance()
        rule.onNodeWithTag("video-card-entry").assertExists()
    }
    @Test fun downloadsCanGroupByPlaylistAndExpandWithoutLosingItems() {
        library(LibraryFilter.Downloads)
        rule.onNodeWithTag("library-group-settings-downloads").performClick()
        capture("downloads-group-dialog")
        rule.onNodeWithTag("library-group-playlist").performClick()
        rule.onNodeWithTag("library-group-confirm").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("collection-playlist:0::p1").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-card-entry").assertDoesNotExist()
        rule.onNodeWithTag("toggle-collection-playlist:0::p1").performClick()
        rule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("video-card-entry"))
        rule.onNodeWithTag("video-card-entry").assertIsDisplayed()
        capture("downloads-grouped")
    }
    @Test fun watchLaterCanGroupByChannelAndReturnToAnUngroupedList() {
        library(LibraryFilter.WatchLater)
        rule.onNodeWithTag("library-group-settings-watchlater").performClick()
        rule.onNodeWithTag("library-group-channel").performClick()
        rule.onNodeWithTag("library-group-confirm").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("collection-channel:7:youtube:artist").fetchSemanticsNodes().isNotEmpty() }
        capture("watch-later-grouped")
        rule.onNodeWithTag("library-group-settings-watchlater").performClick()
        rule.onNodeWithTag("library-group-none").performClick()
        rule.onNodeWithTag("library-group-confirm").performClick()
        rule.onNodeWithTag("video-card-entry").assertExists()
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
