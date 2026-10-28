package com.futo.platformplayer.compose.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MusicWorkflowRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val tracks = List(18) { VideoUiModel("track-$it", "Track $it", "Artist", "", "3:00", sourceId = "spotify") }
    private val playlist = PlaylistUiModel("workflow", "My playlist", "", tracks.map { it.id })

    @Test fun repeatedPlaylistDownloadNeverCancelsOrStartsAgain() {
        var active by mutableStateOf(false)
        var remote by mutableStateOf(false)
        var starts = 0
        var cancels = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) { Surface(Modifier.systemBarsPadding()) {
            CompositionLocalProvider(LocalMusicPresentation provides true) {
                if (remote) RemotePlaylistDetailScreen(
                    RemotePlaylistDetailUiState(playlist = playlist.copy(sourceId = "spotify"), videos = tracks,
                        activeDownloadMediaTypes = if (active) setOf(DownloadMediaType.Audio) else emptySet()),
                    emptyMap(), onVideoClick = {}, onVideoLongClick = {}, onPlayAll = {},
                    onDownloadAll = { starts++; active = true }, onCancelDownloadAll = { cancels++ },
                    onCreateLocalPlaylist = {}, onLoadMore = {})
                else PlaylistDetailScreen(playlist, tracks, onVideoClick = {}, onVideoLongClick = {}, onPlayAll = {},
                    activeDownloadMediaTypes = if (active) setOf(DownloadMediaType.Audio) else emptySet(),
                    onDownloadAllAsAudio = { starts++; active = true }, onCancelDownloadAllAsAudio = { cancels++ })
            }
        } } }
        repeat(2) {
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.download_all_audio)).performClick()
            rule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertIsDisplayed()
            // The button keeps the same click target after replacing its icon with a spinner.
            rule.onNodeWithTag("music-playlist-download").performClick().performClick()
            rule.runOnIdle { assertEquals(it + 1, starts); assertEquals(0, cancels); active = false; remote = true }
        }
    }

    @Test fun reorderIsDraftAndPlayDoesNotSaveWhileSaveUsesVisibleOrder() {
        var saved: List<String>? = null
        var played: List<String>? = null
        var liveUpdates = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ReorderPlaylistDialog(tracks, {}, { saved = it }, currentVideoId = tracks.last().id,
                onOrderChange = { liveUpdates++ }, onPlaybackOrderChange = { liveUpdates++ },
                onPlayOrder = { played = it })
        } }
        rule.onNodeWithTag("playlist-reorder-list").performScrollToIndex(15)
        rule.onNodeWithTag("reorder-shuffle").performClick().assertIsOn().assertIsEnabled()
        rule.runOnIdle { assertNull(saved); assertEquals(0, liveUpdates) }
        rule.onNodeWithTag("reorder-play").performClick()
        rule.runOnIdle { assertNull(saved); assertEquals(tracks.last().id, played!!.first()) }
        rule.onNodeWithTag("reorder-confirm").performClick()
        rule.runOnIdle { assertEquals(played, saved); assertEquals(tracks.map { it.id }.toSet(), saved!!.toSet()) }
    }

    @Test fun titleLengthDoesNotMoveTransportControls() {
        lateinit var player: ExoPlayer
        var title by mutableStateOf("Short")
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                CompositionLocalProvider(LocalMusicPresentation provides true) {
                    MusicNowPlaying(tracks[0].copy(title = title), player, PlaybackUiState(), NowPlayingUiState(), tracks,
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onFullscreen = {})
                }
            } } }
            val before = rule.onNodeWithTag("music-play-pause").fetchSemanticsNode().boundsInRoot
            rule.runOnIdle { title = "A very long song title that will need two or more lines and should never move the playback controls" }
            assertEquals(before, rule.onNodeWithTag("music-play-pause").fetchSemanticsNode().boundsInRoot)
            rule.onNodeWithTag("music-fullscreen").assertIsDisplayed()
        } finally { rule.runOnUiThread { player.release() } }
    }

    @Test fun musicFullscreenUsesArtworkOnLeftAndControlsOnRight() {
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        android.os.SystemClock.sleep(700)
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        try {
            rule.setContent { GrayjayTheme(darkTheme = true, dynamicColor = false) { Surface(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalMusicPresentation provides true) {
                    MusicNowPlaying(tracks[0].copy(title = "A song with a sufficiently long title"), player,
                        PlaybackUiState(), NowPlayingUiState(), tracks, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                        fullscreen = true, onFullscreen = {})
                }
            } } }
            val art = rule.onNodeWithTag("music-player-artwork").fetchSemanticsNode().boundsInRoot
            val controls = rule.onNodeWithTag("music-play-pause").fetchSemanticsNode().boundsInRoot
            assertTrue(art.right < controls.left)
            rule.onNodeWithTag("music-fullscreen").assertIsDisplayed()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "honor-music-fullscreen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            rule.runOnUiThread { player.release(); rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test fun libraryStripDoesNotMoveForAnAlreadyVisibleSelection() {
        var filter by mutableStateOf(LibraryFilter.History)
        rule.setContent { GrayjayTheme(dynamicColor = false) { Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            LibraryScreen(emptyList(), emptyList(), onVideoClick = {}, onVideoLongClick = {}, onPlaylistClick = {},
                onAddSelectionToPlaylist = {}, onQueueSelection = {}, onRemoveSelectionFromHistory = {},
                selectedFilter = filter, onSelectedFilterChange = { filter = it })
        } } }
        val initial = rule.onNodeWithTag("library-filter-history").fetchSemanticsNode().boundsInRoot.left
        rule.runOnIdle { filter = LibraryFilter.Playlists }
        rule.waitForIdle()
        assertEquals(initial, rule.onNodeWithTag("library-filter-history").fetchSemanticsNode().boundsInRoot.left, 1f)
        rule.runOnIdle { filter = LibraryFilter.Downloads }
        rule.waitForIdle()
        val selected = rule.onNodeWithTag("library-filter-downloads").fetchSemanticsNode().boundsInRoot
        val strip = rule.onNodeWithTag("library-filter-strip").fetchSemanticsNode().boundsInRoot
        assertTrue(selected.left >= strip.left && selected.right <= strip.right)
    }

    @Test fun appEntersDedicatedMusicFullscreenAndReturnsToNowPlaying() {
        val video = tracks.first().copy(playbackAudioOnly = true)
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video, tracks[1]), libraryVideos = listOf(video, tracks[1]),
            sources = listOf(SourceUiModel("spotify", "Spotify", "", 0L, true)),
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id, tracks[1].id)),
            nowPlaying = NowPlayingUiState(video = video),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var queuedSelection: String? = null
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onPlayQueuedVideo = { queuedSelection = it }
        }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
            rule.onNodeWithTag("music-fullscreen").performScrollTo().performClick()
            rule.onNodeWithTag("music-fullscreen-player").assertIsDisplayed()
            rule.onNodeWithTag("fullscreen-player").assertDoesNotExist()
            rule.onNodeWithTag("music-fullscreen").performScrollTo().performClick()
            rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
            rule.onNodeWithTag("music-fullscreen").performScrollTo().performClick()
            rule.onNodeWithTag("music-open-queue").performScrollTo().performClick()
            rule.onNodeWithText(tracks[1].title).performClick()
            rule.runOnIdle { assertEquals(tracks[1].id, queuedSelection) }
        } finally { rule.runOnUiThread { player.release() } }
    }

    @Test fun polishMusicLibraryUsesFavoritesAndAllTabLabelsAreTranslated() {
        val configuration = android.content.res.Configuration(rule.activity.resources.configuration).apply { setLocale(java.util.Locale("pl")) }
        val context = rule.activity.createConfigurationContext(configuration)
        rule.setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides context,
            androidx.compose.ui.platform.LocalConfiguration provides configuration, LocalMusicPresentation provides true,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides rule.activity,
            androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides rule.activity) {
            GrayjayTheme(dynamicColor = false) { Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                LibraryScreen(emptyList(), emptyList(), onVideoClick = {}, onVideoLongClick = {}, onPlaylistClick = {},
                    onAddSelectionToPlaylist = {}, onQueueSelection = {}, onRemoveSelectionFromHistory = {},
                    selectedFilter = LibraryFilter.WatchLater)
            } }
        } }
        rule.onAllNodesWithText("Ulubione").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("Do obejrzenia").assertCountEquals(0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "honor-polish-favorites.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
