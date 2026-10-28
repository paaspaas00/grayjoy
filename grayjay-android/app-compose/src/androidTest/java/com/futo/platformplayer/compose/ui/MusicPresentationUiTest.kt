package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MusicPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    private fun videos(): List<VideoUiModel> {
        val art = File(rule.activity.cacheDir, "music-ui-art.png")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(39, 99, 85))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(187, 227, 192); textSize = 40f }
            drawCircle(290f, 110f, 70f, paint)
            drawText("GRAYJOY", 28f, 295f, paint)
            paint.textSize = 22f
            drawText("Music collection", 28f, 340f, paint)
        }
        art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return List(14) { i -> VideoUiModel("song-$i", listOf("Non Lo Diciamo A Nessuno", "Distrutto", "Athena", "Perché Ti Conosco")[i % 4],
            "Cesare Cremonini", "", "3:44", sourceId = "spotify", thumbnailUrl = art.absolutePath) }
    }
    @After fun release() { rule.runOnUiThread { player?.release() } }

    @Test fun musicPlaylistUsesCompactRowsAndOpensFullHeightReorder() {
        val videos = videos()
        val playlist = PlaylistUiModel("mix", "Alaska Baby", "Cesare Cremonini · 2024", videos.map { it.id }, thumbnailUrl = videos[0].thumbnailUrl)
        rule.setContent { GrayjayTheme(dynamicColor = false) { Surface(Modifier.systemBarsPadding()) {
            CompositionLocalProvider(LocalMusicPresentation provides true) {
                PlaylistDetailScreen(playlist, videos, onVideoClick = {}, onVideoLongClick = {}, onPlayAll = {})
            }
        } } }
        rule.onNodeWithTag("music-playlist-play").assertIsDisplayed()
        rule.onNodeWithTag("shuffle-playlist-mix").assertDoesNotExist()
        capture("oct28-music-playlist")
        rule.onNodeWithTag("playlist-open-reorder").performScrollTo().performClick()
        rule.onNodeWithTag("reorder-shuffle").assertIsDisplayed()
        assertTrue(rule.onNodeWithTag("playlist-reorder-list").fetchSemanticsNode().boundsInRoot.height > 1000f)
        capture("oct28-music-reorder")
    }

    @Test fun shuffleRestoresOrderAndDragMovesOnlyOneTrack() {
        val videos = videos()
        var confirmed: List<String>? = null
        var shuffled = false
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            ReorderPlaylistDialog(videos, onDismiss = {}, onConfirm = { confirmed = it }, currentVideoId = "song-3",
                onShuffleChange = { shuffled = it })
        } }
        rule.onNodeWithTag("reorder-shuffle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("reorder-shuffle").assertIsOn()
        rule.onNodeWithTag("reorder-shuffle").performClick()
        rule.onNodeWithTag("reorder-confirm").performClick()
        assertEquals(videos.map { it.id }, confirmed)
        assertFalse(shuffled)
        rule.onNodeWithTag("playlist-reorder-list").performScrollToIndex(0)
        val first = rule.onNodeWithTag("playlist-reorder-handle-song-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val third = rule.onNodeWithTag("playlist-reorder-handle-song-2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("playlist-reorder-list").performTouchInput {
            val bounds = rule.onNodeWithTag("playlist-reorder-list").fetchSemanticsNode().boundsInRoot
            swipe(Offset(first.center.x - bounds.left, first.center.y - bounds.top),
                Offset(third.center.x - bounds.left, third.bottom - bounds.top - 2), durationMillis = 700)
        }
        rule.onNodeWithTag("reorder-confirm").performClick()
        assertEquals(videos.size, confirmed!!.size)
        assertEquals(videos.map { it.id }.toSet(), confirmed!!.toSet())
        assertTrue(confirmed!!.indexOf("song-0") >= 2)
    }

    @Test fun nowPlayingUsesArtworkAndNeverShowsResumePrompt() {
        val videos = videos()
        rule.runOnUiThread { rule.activity.enableEdgeToEdge(); player = ExoPlayer.Builder(rule.activity).build() }
        rule.setContent { GrayjayTheme(dynamicColor = false) { Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            CompositionLocalProvider(LocalMusicPresentation provides true) {
                MusicNowPlaying(videos[0], player!!, PlaybackUiState(currentVideoId = videos[0].id,
                    queueVideoIds = videos.map { it.id }, durationMs = 224_000), NowPlayingUiState(resumePositionFraction = .5f),
                    videos, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        } } }
        rule.onNodeWithTag("music-now-playing").assertIsDisplayed()
        rule.onNodeWithTag("media-player").assertDoesNotExist()
        rule.onNodeWithTag("continue-from-history").assertDoesNotExist()
        capture("oct28-music-player")
        rule.onNodeWithTag("music-open-queue").performScrollTo().performClick()
        rule.onAllNodesWithText(videos[1].title).onFirst().assertIsDisplayed()
    }

    @Test fun filledSearchAndMusicHomeRenderInDarkMode() {
        val videos = videos()
        val sections = listOf(MusicHomeSection("tracks", "Brani di tendenza", videos.take(6).map {
            MusicHomeItem(it.id, it.title, "track", it.thumbnailUrl) }),
            MusicHomeSection("albums", "Album e singoli più popolari", videos.take(6).map {
                MusicHomeItem(it.id, it.title, "album", it.thumbnailUrl) }))
        rule.setContent { GrayjayTheme(darkTheme = true, dynamicColor = false) { Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            Column {
                var query by remember { mutableStateOf("") }
                PreferencesSearchField(query) { query = it }
                MusicHomeScreen(HomeUiState(musicSections = sections), emptyList(), {}, {}, {}, {}, Modifier.weight(1f))
            }
        } } }
        rule.onNodeWithTag("settings-search").assertIsDisplayed()
        rule.onNodeWithTag("music-home").assertIsDisplayed()
        capture("oct28-music-home-dark")
    }

    @Test fun musicArtworkSwipesToMiniPlayerAndBackWithoutOpeningOnTap() {
        val video = videos().first().copy(playbackAudioOnly = true)
        val state = mutableStateOf(GrayjayUiState(videos = listOf(video),
            sources = listOf(SourceUiModel("spotify", "Spotify", "", 0L, true)),
            playback = PlaybackUiState(currentVideoId = video.id, queueVideoIds = listOf(video.id)),
            nowPlaying = NowPlayingUiState(video = video),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id)))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player!!, actions) } }
        rule.onNodeWithTag("music-player-artwork").performTouchInput { swipe(center, center + Offset(0f, 1300f), 700) }
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        rule.onNodeWithTag("mini-player").performTouchInput { click(Offset(width * .35f, height * .5f)) }
        rule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        capture("oct28-music-mini")
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(center, center.copy(y = -1200f), 500) }
        rule.onNodeWithTag("music-player-artwork").assertIsDisplayed()
        rule.onNodeWithTag("now-playing-close").assertIsDisplayed()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
