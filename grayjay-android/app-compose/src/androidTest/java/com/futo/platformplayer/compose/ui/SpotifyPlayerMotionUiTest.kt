package com.futo.platformplayer.compose.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

class SpotifyPlayerMotionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    @Before fun setup() = rule.runOnUiThread {
        originalOrientation = rule.activity.requestedOrientation
        rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity.applicationContext).build()
    }
    @After fun cleanup() = rule.runOnUiThread { player.release(); rule.activity.requestedOrientation = originalOrientation }
    private fun tracks(): List<VideoUiModel> {
        val pkg = InstrumentationRegistry.getInstrumentation().context.packageName
        return (0..2).map { i -> VideoUiModel("cover-$i", if (i == 2) "A much longer song title that takes two lines to display" else "Song $i",
            "Artist $i", "", "3:00", sourceId = "spotify", thumbnailUrl = "android.resource://$pkg/drawable/${if (i == 1) "audit_earth" else "audit_moon"}") }
    }
    private fun directPlayer(fullscreen: Boolean = false): MutableState<Boolean> {
        val tracks = tracks()
        var index by mutableIntStateOf(1)
        val left = mutableStateOf(false)
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { Surface(Modifier.fillMaxSize()
            .then(if (fullscreen) Modifier else Modifier.systemBarsPadding())) {
            CompositionLocalProvider(LocalMusicPresentation provides true, LocalInteractionControls provides InteractionControls(leftHanded = left.value)) {
                MusicNowPlaying(tracks[index], player, PlaybackUiState(currentVideoId = tracks[index].id,
                    queueVideoIds = tracks.map { it.id }, durationMs = 180_000), NowPlayingUiState(video = tracks[index]), tracks,
                    onToggle = {}, onPrevious = { index-- }, onNext = { index++ }, onSeek = {}, onWatchLater = {},
                    onDownload = {}, onAddToPlaylist = {}, onRetry = {}, onPlay = {}, onMore = {}, onSpeed = {}, onArtworkBounds = {},
                    fullscreen = fullscreen, onFullscreen = {})
            }
        } } }
        return left
    }
    @Test fun nextAndPreviousCoversFadeAndMoveInOppositeDirections() {
        directPlayer()
        rule.onNodeWithTag("music-next").assertIsEnabled()
        val x = rule.onNodeWithTag("music-player-artwork").fetchSemanticsNode().positionInRoot.x
        val controlsTop = rule.onNodeWithTag("music-transport-controls").fetchSemanticsNode().boundsInRoot.top
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("music-next").performClick()
        rule.mainClock.advanceTimeBy(96)
        assertTrue(rule.onNodeWithTag("music-cover-cover-1").fetchSemanticsNode().positionInRoot.x < x)
        assertTrue(rule.onNodeWithTag("music-cover-cover-2").fetchSemanticsNode().positionInRoot.x > x)
        checkMetadataDirection("cover-1", "cover-2", forward = true)
        capture("spotify-next-cover-middle")
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("music-cover-cover-1").assertDoesNotExist()
        assertEquals("Controls must not move for a two-line title", controlsTop,
            rule.onNodeWithTag("music-transport-controls").fetchSemanticsNode().boundsInRoot.top, 2f)
        rule.onNodeWithTag("music-previous").performClick()
        rule.mainClock.advanceTimeBy(96)
        assertTrue(rule.onNodeWithTag("music-cover-cover-2").fetchSemanticsNode().positionInRoot.x > x)
        assertTrue(rule.onNodeWithTag("music-cover-cover-1").fetchSemanticsNode().positionInRoot.x < x)
        checkMetadataDirection("cover-2", "cover-1", forward = false)
        capture("spotify-previous-cover-middle")
        rule.mainClock.advanceTimeBy(400); rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("music-cover-cover-2").assertDoesNotExist()
        checkOnlyCurrentMetadata("cover-1", "cover-2")
    }
    @Test fun secondaryControlsAreLowerAndTightlyCentered() {
        directPlayer()
        rule.onNodeWithTag("music-secondary-controls").performScrollTo()
        val transport = rule.onNodeWithTag("music-transport-controls").fetchSemanticsNode().boundsInRoot
        val queue = rule.onNodeWithTag("music-open-queue").fetchSemanticsNode().boundsInRoot
        val download = rule.onNodeWithTag("music-download").fetchSemanticsNode().boundsInRoot
        val add = rule.onNodeWithTag("music-add-playlist").fetchSemanticsNode().boundsInRoot
        val fullscreen = rule.onNodeWithTag("music-fullscreen").fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        assertTrue(queue.top - transport.bottom >= 20f * density)
        // IconButton keeps a 48dp touch target around its smaller visual container.
        assertEquals(60f * density, download.center.x - queue.center.x, 2f)
        assertEquals(60f * density, add.center.x - download.center.x, 2f)
        assertEquals(60f * density, fullscreen.center.x - add.center.x, 2f)
        capture("spotify-secondary-controls")
    }
    @Test fun rapidlyReversingTheTrackChangeFinishesWithOnlyTheCurrentCover() {
        directPlayer()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("music-next").performClick()
        rule.mainClock.advanceTimeBy(96)
        rule.onNodeWithTag("music-previous").performClick()
        rule.mainClock.advanceTimeBy(80)
        capture("spotify-cover-rapid-reversal")
        rule.mainClock.advanceTimeBy(500); rule.mainClock.autoAdvance = true
        rule.onAllNodesWithTag("music-cover-cover-1").assertCountEquals(1)
        rule.onNodeWithTag("music-cover-cover-2").assertDoesNotExist()
        checkOnlyCurrentMetadata("cover-1", "cover-2")
    }
    @Test fun fullscreenTitlesAndArtistsFollowTheCoverInBothDirectionsAndHandedness() {
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5000) { rule.activity.resources.configuration.screenWidthDp > rule.activity.resources.configuration.screenHeightDp }
        val left = directPlayer(fullscreen = true)
        for (leftHanded in listOf(false, true)) {
            rule.runOnIdle { left.value = leftHanded }
            val controlsTop = rule.onNodeWithTag("music-transport-controls").fetchSemanticsNode().boundsInRoot.top
            val artX = rule.onNodeWithTag("music-player-artwork").fetchSemanticsNode().positionInRoot.x
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag("music-next").performClick()
            rule.mainClock.advanceTimeBy(96)
            checkMetadataDirection("cover-1", "cover-2", forward = true)
            assertTrue(rule.onNodeWithTag("music-cover-cover-1").fetchSemanticsNode().positionInRoot.x < artX)
            assertTrue(rule.onNodeWithTag("music-cover-cover-2").fetchSemanticsNode().positionInRoot.x > artX)
            capture("spotify-fullscreen-next-${if (leftHanded) "left" else "right"}")
            rule.mainClock.advanceTimeBy(400)
            assertEquals(controlsTop, rule.onNodeWithTag("music-transport-controls").fetchSemanticsNode().boundsInRoot.top, 2f)
            rule.onNodeWithTag("music-previous").performClick()
            rule.mainClock.advanceTimeBy(96)
            checkMetadataDirection("cover-2", "cover-1", forward = false)
            assertTrue(rule.onNodeWithTag("music-cover-cover-2").fetchSemanticsNode().positionInRoot.x > artX)
            assertTrue(rule.onNodeWithTag("music-cover-cover-1").fetchSemanticsNode().positionInRoot.x < artX)
            capture("spotify-fullscreen-previous-${if (leftHanded) "left" else "right"}")
            rule.mainClock.advanceTimeBy(400); rule.mainClock.autoAdvance = true
            checkOnlyCurrentMetadata("cover-1", "cover-2")
        }
    }
    @Test fun lateMetadataForTheSameTrackDoesNotStartASlide() {
        var title by mutableStateOf("Initial title")
        rule.setContent { GrayjayTheme { Surface {
            AnimatedMusicTrackText(MusicCoverFrame("same", "", null, 1), title, "title", animate = true,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, lines = 2)
        } } }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { title = "Updated title" }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("Initial title").assertDoesNotExist()
        rule.onNodeWithText("Updated title").assertExists()
        assertEquals(rule.onNodeWithTag("music-title-slot").fetchSemanticsNode().positionInRoot.x,
            rule.onNodeWithTag("music-title-same").fetchSemanticsNode().positionInRoot.x, 1f)
        rule.mainClock.autoAdvance = true
    }
    @Test fun fullscreenButtonStaysInTheSafeTopCornerForBothHands() {
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5000) { rule.activity.resources.configuration.screenWidthDp > rule.activity.resources.configuration.screenHeightDp }
        val left = directPlayer(fullscreen = true)
        val density = rule.activity.resources.displayMetrics.density
        fun check(leftHanded: Boolean) {
            val viewport = rule.onNodeWithTag("music-now-playing").fetchSemanticsNode().boundsInRoot
            val button = rule.onNodeWithTag("music-fullscreen").fetchSemanticsNode().boundsInRoot
            val inset = if (leftHanded) button.left - viewport.left else viewport.right - button.right
            assertTrue("Fullscreen button needs a comfortable side inset", inset in (16f * density)..(40f * density))
            assertTrue(button.top - viewport.top <= 20f * density)
            val heading = rule.onNodeWithTag("music-fullscreen-heading").fetchSemanticsNode().boundsInRoot
            assertTrue("Now playing needs comfortable space above it", heading.top - viewport.top in (26f * density)..(44f * density))
            rule.onNodeWithTag("music-secondary-controls").performScrollTo()
            val secondary = rule.onNodeWithTag("music-secondary-controls").fetchSemanticsNode().boundsInRoot
            assertTrue(button.bottom < secondary.top)
            val art = rule.onNodeWithTag("music-player-artwork").fetchSemanticsNode().boundsInRoot
            assertTrue("Heading must be above the artwork", heading.bottom < art.top)
            assertEquals("Heading must stay centered over the artwork side", art.center.x, heading.center.x, 2f)
            val controls = rule.onNodeWithTag("music-transport-controls").fetchSemanticsNode().boundsInRoot
            assertTrue(if (leftHanded) controls.center.x < art.center.x else controls.center.x > art.center.x)
        }
        check(false); capture("spotify-fullscreen-right-hand")
        rule.runOnIdle { left.value = true }
        check(true); capture("spotify-fullscreen-left-hand")
    }
    @Test fun homeTrackUsesTheContainerMorphWithoutAlsoStartingTheCoverSlide() {
        val video = tracks()[1]
        val previous = tracks()[0]
        var state by mutableStateOf(GrayjayUiState(sources = listOf(SourceUiModel("spotify", "Spotify", "", 0L, true)),
            videos = listOf(previous, video), home = HomeUiState(musicSections = listOf(MusicHomeSection("tracks", "Songs",
                listOf(MusicHomeItem(video.id, video.title, "track", video.thumbnailUrl))))),
            nowPlaying = NowPlayingUiState(video = previous), playback = PlaybackUiState(currentVideoId = previous.id, queueVideoIds = listOf(previous.id)),
            externalNavigation = ExternalNavigationUiModel(1L, ExternalNavigationKind.Video, previous.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
            onOpenVideo = { state = state.copy(nowPlaying = NowPlayingUiState(video = video),
                playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id))) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("music-player-artwork").performTouchInput {
            swipe(center, center.copy(y = centerY + 1500f), 600)
        }
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        val source = rule.onNodeWithTag("music-home-track-${video.id}").assertIsDisplayed()
        val width = source.fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.autoAdvance = false
        val click = source.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnUiThread { click() }
        var time = 0L
        val sizes = mutableListOf<Float>()
        for (frame in listOf(48L, 96L, 160L, 240L, 320L, 480L, 700L)) {
            rule.mainClock.advanceTimeBy(frame - time); time = frame; rule.waitForIdle()
            rule.onAllNodesWithTag("card-player-morph-art").fetchSemanticsNodes().firstOrNull()?.let { sizes.add(it.boundsInRoot.width) }
            if (frame == 240L) {
                rule.onAllNodesWithTag("music-cover-${previous.id}").assertCountEquals(0)
                for (role in listOf("title", "artist")) rule.onNodeWithTag("music-$role-${previous.id}").assertDoesNotExist()
                capture("spotify-home-morph-middle")
            }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("card-player-morph").assertDoesNotExist()
        val target = rule.onNodeWithTag("music-player-artwork").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.width
        assertTrue("Home artwork must pass through intermediate sizes", sizes.any { it > width + 5f && it < target - 5f })
        rule.onAllNodesWithTag("music-cover-${video.id}").assertCountEquals(1)
        capture("spotify-home-morph-complete")
    }
    private fun checkMetadataDirection(previous: String, next: String, forward: Boolean) {
        for (role in listOf("title", "artist")) {
            val x = rule.onNodeWithTag("music-$role-slot").fetchSemanticsNode().positionInRoot.x
            val outgoing = rule.onNodeWithTag("music-$role-$previous").fetchSemanticsNode().positionInRoot.x
            val incoming = rule.onNodeWithTag("music-$role-$next").fetchSemanticsNode().positionInRoot.x
            assertTrue("Outgoing $role must follow the cover", if (forward) outgoing < x else outgoing > x)
            assertTrue("Incoming $role must follow the cover", if (forward) incoming > x else incoming < x)
        }
    }
    private fun checkOnlyCurrentMetadata(current: String, previous: String) {
        for (role in listOf("title", "artist")) {
            rule.onAllNodesWithTag("music-$role-$current").assertCountEquals(1)
            rule.onNodeWithTag("music-$role-$previous").assertDoesNotExist()
        }
    }
    private fun capture(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(60)
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
