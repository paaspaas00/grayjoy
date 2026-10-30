package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.recognition.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RecognitionResultTransitionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var lookups = 0
    private val starts = mutableListOf<Boolean>()
    private var previousLeftHanded = false
    @Before fun initialize() { rule.runOnUiThread {
        val prefs = InteractionFeatures(rule.activity)
        previousLeftHanded = prefs.leftHanded; prefs.leftHanded = false
        player = ExoPlayer.Builder(rule.activity).build()
    } }
    @After fun cleanup() { rule.runOnUiThread { InteractionFeatures(rule.activity).leftHanded = previousLeftHanded; player.release() } }

    private fun showResult(music: Boolean = true, delayed: kotlinx.coroutines.CompletableDeferred<VideoUiModel>? = null,
        alternateDetails: Boolean = false): VideoUiModel {
        val pkg = InstrumentationRegistry.getInstrumentation().context.packageName
        val artwork = "android.resource://$pkg/drawable/audit_earth"
        val song = RecognizedSong("Test song", "Test artist", artwork)
        val video = VideoUiModel("recognized-result",
            if (alternateDetails) "Test song — A long official live recording title, with extra source information" else "Test song",
            if (alternateDetails) "Test artist and the full orchestra" else "Test artist", "", "3:00", sourceId = "youtube",
            thumbnailUrl = if (alternateDetails) "android.resource://$pkg/drawable/audit_moon" else artwork, isMusic = true)
        var state by mutableStateOf(GrayjayUiState(sources = listOf(SourceUiModel(if (music) "spotify" else "youtube", "Source", "", 0, true))))
        val actions = GrayjayAppActions().apply {
            onResolveRecognizedSong = { lookups++; delayed?.await() ?: video }
            onOpenRecognizedSong = { id, start ->
                starts.add(start)
                player.playWhenReady = start
                state = state.copy(videos = listOf(video), libraryVideos = listOf(video),
                    nowPlaying = NowPlayingUiState(video = video),
                    playback = PlaybackUiState(currentVideoId = id, durationMs = 180000, isPlaying = start, queueVideoIds = listOf(id)),
                    externalNavigation = ExternalNavigationUiModel(1L, ExternalNavigationKind.Video, id))
            }
            onExternalNavigationHandled = { state = state.copy(externalNavigation = null) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = true) {
            CompositionLocalProvider(LocalSongRecognizer provides { _, _ -> song }) { GrayjayApp(state, player, actions) }
        } }
        rule.onNodeWithTag("nav-search").performTouchInput { longClick() }
        rule.onNodeWithTag("recognition-swipe-hint").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, lookups); assertTrue(starts.isEmpty()) }
        return video
    }
    @Test fun swipeMorphsToMusicNowPlayingPausedAndDoesNotSearchTwice() {
        showResult()
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeUp() }
        rule.onNodeWithTag("music-now-playing").assertIsDisplayed()
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf(false), starts); assertFalse(player.playWhenReady); assertEquals(1, lookups) }
        capture("recognition-paused-now-playing")
    }
    @Test fun playButtonKeepsAutoplay() {
        showResult()
        rule.onNodeWithTag("recognition-play").performClick()
        rule.onNodeWithTag("music-now-playing").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf(true), starts); assertTrue(player.playWhenReady) }
    }
    @Test fun swipeAlsoOpensTheStandardVideoPlayerInPause() {
        showResult(music = false)
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeUp() }
        rule.onNodeWithTag("media-player").assertIsDisplayed()
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf(false), starts); assertFalse(player.playWhenReady) }
        capture("recognition-paused-video-now-playing")
    }
    @Test fun intermediateMorphFrameKeepsTheArtworkAndFinishesCleanly() {
        showResult()
        capture("recognition-result-before-swipe")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(128)
        capture("recognition-result-mid-morph")
        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("music-player-artwork").assertIsDisplayed()
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertFalse(player.playWhenReady) }
        capture("recognition-result-morph-complete")
    }
    @Test fun capturesTheContainerMorphAtFixedFrameTimes() {
        showResult()
        val sourceWidth = rule.onNodeWithTag("recognition-result-art").fetchSemanticsNode().boundsInRoot.width
        rule.waitUntil(5000) {
            val pixels = rule.onNodeWithTag("recognition-result-art").captureToImage().toPixelMap()
            pixels[pixels.width / 2, (pixels.height * .85f).toInt()].red > .3f
        }
        capture("morph-film-000")
        rule.mainClock.autoAdvance = false
        val open = rule.onNodeWithTag("song-recognition").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first()
        rule.runOnUiThread { assertTrue(open.action()) }
        var time = 0L
        val artworkWidths = mutableListOf<Float>()
        for (frame in listOf(32L, 96L, 160L, 224L, 288L, 352L, 480L, 800L)) {
            rule.mainClock.advanceTimeBy(frame - time)
            time = frame
            capture("morph-film-${frame.toString().padStart(3, '0')}")
            val node = rule.onAllNodesWithTag("recognition-morph-art").fetchSemanticsNodes().firstOrNull()
            if (node != null && node.boundsInRoot.width > 10f) {
                artworkWidths.add(node.boundsInRoot.width)
                if (frame in setOf(96L, 160L, 224L)) {
                    val pixels = rule.onNodeWithTag("recognition-morph-art").captureToImage().toPixelMap()
                    assertTrue("The loaded cover must not flash back to a grey placeholder",
                        pixels[pixels.width / 2, (pixels.height * .85f).toInt()].red > .3f)
                }
            }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("music-now-playing").assertIsDisplayed()
        val targetWidth = rule.onNodeWithTag("music-player-artwork").fetchSemanticsNode().boundsInRoot.width
        assertTrue("There must be genuinely intermediate cover sizes", artworkWidths.any { it > sourceWidth + 5 && it < targetWidth - 5 })
        assertTrue("Cover size must progress monotonically", artworkWidths.zipWithNext().all { (a, b) -> b >= a - 1f })
        rule.runOnIdle { assertFalse(player.playWhenReady) }
    }
    @Test fun differentArtworkAndLongSourceTitlesDoNotDistortDuringTheMorph() {
        val video = showResult(alternateDetails = true)
        rule.mainClock.autoAdvance = false
        val open = rule.onNodeWithTag("song-recognition").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first()
        rule.runOnUiThread { open.action() }
        rule.mainClock.advanceTimeBy(200)
        capture("morph-long-title-middle")
        rule.mainClock.advanceTimeBy(650)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText(video.title).assertIsDisplayed()
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertFalse(player.playWhenReady); assertEquals(1, lookups) }
        capture("morph-long-title-final")
    }
    @Test fun videoDestinationMorphKeepsTheCoverAspectAndPausedIntent() {
        showResult(music = false)
        rule.mainClock.autoAdvance = false
        val open = rule.onNodeWithTag("song-recognition").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first()
        rule.runOnUiThread { open.action() }
        var started = false
        for (frame in 0 until 30) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val bounds = rule.onAllNodesWithTag("recognition-morph-art").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            if (bounds != null && bounds.width > 10f) { started = true; break }
        }
        assertTrue("The video destination must have a real morph, not a timeout followed by a cut", started)
        rule.mainClock.advanceTimeBy(128)
        val middle = rule.onNodeWithTag("recognition-morph-art").fetchSemanticsNode().boundsInRoot
        assertTrue("The cover must be opening towards the wide video viewport", middle.width > middle.height)
        capture("morph-video-middle")
        rule.mainClock.advanceTimeBy(650)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("media-player").assertIsDisplayed()
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertFalse(player.playWhenReady) }
    }
    @Test fun aSmallCancelledSwipeDoesNotOpenOrPlayAnything() {
        showResult()
        rule.onNodeWithTag("song-recognition").performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, 6f * rule.activity.resources.displayMetrics.density), 300)
        }
        rule.onNodeWithTag("song-recognition").assertIsDisplayed()
        rule.runOnIdle { assertTrue(starts.isEmpty()); assertFalse(player.playWhenReady) }
    }
    @Test fun aSheetWithoutARecognizedResultCannotOpenNowPlaying() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalSongRecognizer provides { _, _ -> null }) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) }
        } }
        rule.onNodeWithTag("nav-search").performTouchInput { longClick() }
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeUp() }
        rule.onNodeWithTag("song-recognition").assertIsDisplayed()
        rule.onNodeWithTag("music-now-playing").assertDoesNotExist()
    }
    @Test fun leftHandedModeMovesBothActionsToTheLeftWithPlayClosestToTheEdge() {
        showResult()
        val rightPlay = rule.onNodeWithTag("recognition-play").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { InteractionFeatures(rule.activity).leftHanded = true }
        val actions = rule.onNodeWithTag("recognition-actions").fetchSemanticsNode().boundsInRoot
        val play = rule.onNodeWithTag("recognition-play").fetchSemanticsNode().boundsInRoot
        val close = rule.onNodeWithTag("recognition-close").fetchSemanticsNode().boundsInRoot
        assertTrue(play.center.x < actions.center.x && play.left < rightPlay.left)
        assertTrue(play.right <= close.left)
        capture("recognition-left-handed-actions")
    }
    @Test fun swipeDownDismissesTheResultAndDoesNotPlayIt() {
        showResult()
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeDown() }
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertTrue(starts.isEmpty()); assertFalse(player.playWhenReady) }
        rule.onNodeWithTag("nav-library").performTouchInput { click() }
        rule.onNodeWithTag("library-filter-history").assertIsDisplayed()
    }
    @Test fun swipeDownDuringListeningCancelsTheRecognition() {
        var stopped = false
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalSongRecognizer provides { level, _ ->
                try { level(RecognitionAudioFrame(.4f)); kotlinx.coroutines.awaitCancellation() } finally { stopped = true }
            }) { GrayjayApp(GrayjayUiState(), player, GrayjayAppActions()) }
        } }
        rule.onNodeWithTag("nav-search").performTouchInput { longClick() }
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeDown() }
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        rule.runOnIdle { assertTrue(stopped) }
    }
    @Test fun downwardDismissalCancelsAnOpeningWaitingForTheLookup() {
        val delayed = kotlinx.coroutines.CompletableDeferred<VideoUiModel>()
        val video = showResult(delayed = delayed)
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeUp() }
        rule.onNodeWithTag("song-recognition").performTouchInput { swipeDown() }
        rule.onNodeWithTag("song-recognition").assertDoesNotExist()
        delayed.complete(video)
        rule.runOnIdle { assertTrue(starts.isEmpty()) }
    }
    private fun capture(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(60)
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
