package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class HistoryPlaylistGroupingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private lateinit var fixtures: File
    @After fun cleanup() {
        if (::player.isInitialized) rule.runOnUiThread { player.release() }
        if (::fixtures.isInitialized) fixtures.deleteRecursively()
    }

    @Test fun foldersExpandInlineRemainSeparateAndSupportSelectionAndThePreference() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        fixtures = File(app.cacheDir, "history-thumbs-${UUID.randomUUID()}").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val colors = listOf(0xFF195C7A.toInt(), 0xFF9B465C.toInt(), 0xFFAA6E28.toInt())
        fun thumbnail(index: Int): String {
            val file = File(fixtures, "$index.png")
            val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(colors[index % colors.size])
            canvas.drawCircle(245f, 35f, 100f, Paint().apply { color = Color.WHITE; alpha = 35 })
            canvas.drawText("${index + 1}", 26f, 134f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 106f })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            return file.toURI().toString()
        }
        val origin = HistoryPlaylistOrigin("local", "", "Musica per il viaggio", "latest")
        val videos = (0..7).map { index -> VideoUiModel("grouped-$index", "Brano ${index + 1}", "Artista", "", "3:24",
            thumbnailUrl = thumbnail(index), lastWatchedAt = now - index * 60_000L,
            historyPlaylistOrigin = when(index) { in 0..2 -> origin; 3 -> null; in 4..5 -> origin.copy(runId = "older");
                else -> origin.copy(playlistId = "https://example.test/list", sourceId = "youtube", title = "Playlist online", runId = "remote") }) }
        val playlist = PlaylistUiModel("local", "Musica per il viaggio", "", videos.take(3).map { it.id })
        var state by mutableStateOf(GrayjayUiState(videos = videos, libraryVideos = videos, playlists = listOf(playlist),
            sources = listOf(SourceUiModel("youtube", "YouTube", "", 0, true))))
        var queued = emptyList<String>()
        var openedRemote: PlaylistUiModel? = null
        val actions = GrayjayAppActions().apply {
            onGroupHistoryByPlaylistChange = { state = state.copy(groupHistoryByPlaylist = it) }
            onQueueVideos = { queued = it }
            onLoadRemotePlaylist = {
                openedRemote = it
                state = state.copy(remotePlaylistDetail = RemotePlaylistDetailUiState(playlist = it,
                    errorMessage = "Playlist no longer available"))
            }
        }
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var dark by mutableStateOf(true)
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = dark) { GrayjayApp(state, player, actions) } }
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("video-card-grouped-0").assertIsDisplayed()
        toggleGrouping()
        rule.runOnIdle { assertTrue(state.groupHistoryByPlaylist) }
        val group = "history-group:latest:grouped-2"
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(group).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(group).assertIsDisplayed()
        rule.onNodeWithTag("open-$group").assertIsDisplayed().performClick()
        rule.onNodeWithTag("history-group-toggle").assertDoesNotExist()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("history-group-settings").assertIsDisplayed()
        rule.onNodeWithTag(group).assertIsDisplayed()
        rule.runOnIdle { state = state.copy(playlists = emptyList()) }
        rule.onNodeWithTag("open-$group").performClick()
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.playlist_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.ok)).performClick()
        rule.onNodeWithTag(group).assertIsDisplayed()
        rule.onNodeWithTag("video-card-grouped-0").assertDoesNotExist()
        screenshot("history-groups-collapsed-dark")
        rule.onNodeWithTag("toggle-$group").performClick()
        rule.onNodeWithTag("video-card-grouped-0").assertIsDisplayed()
        screenshot("history-groups-expanded-dark")
        rule.runOnIdle { dark = false }
        screenshot("history-groups-expanded-light")
        rule.onNodeWithTag("nav-search").performClick()
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag(group).assertIsDisplayed()
        rule.onNodeWithTag("video-card-grouped-0").assertDoesNotExist()
        rule.onNodeWithTag("toggle-$group").performClick()
        rule.onNodeWithTag("video-card-grouped-0").assertIsDisplayed()
        // Reselecting the active navbar destination also starts with closed folders.
        rule.onNodeWithTag("nav-library").performClick()
        rule.onNodeWithTag("video-card-grouped-0").assertDoesNotExist()
        rule.onNodeWithTag("toggle-$group").performClick()
        rule.onNodeWithTag("toggle-$group").performClick()
        rule.onNodeWithTag("video-card-grouped-0").assertDoesNotExist()
        rule.onNodeWithTag(group).performTouchInput { longClick() }
        rule.onNodeWithTag("history-selection-bar").assertIsDisplayed()
        rule.onNodeWithTag("history-enqueue").performClick()
        rule.runOnIdle { assertEquals(setOf("grouped-0", "grouped-1", "grouped-2"), queued.toSet()) }
        rule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("history-group:older:grouped-5"))
        rule.onNodeWithTag("history-group:older:grouped-5").assertIsDisplayed()
        rule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("history-group:remote:grouped-7"))
        rule.onNodeWithTag("history-group:remote:grouped-7").assertIsDisplayed()
        rule.onNodeWithTag("open-history-group:remote:grouped-7").performClick()
        rule.runOnIdle { assertEquals("https://example.test/list", openedRemote?.id) }
        rule.onNodeWithText("Playlist no longer available").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("history-group:remote:grouped-7").assertIsDisplayed()
        rule.onNodeWithTag("library-list").performScrollToIndex(0)
        toggleGrouping()
        rule.runOnIdle { assertFalse(state.groupHistoryByPlaylist) }
        rule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("video-card-grouped-7"))
        rule.onNodeWithTag("video-card-grouped-7").assertIsDisplayed()
    }

    private fun toggleGrouping() {
        rule.onNodeWithTag("history-group-settings").performClick()
        rule.onNodeWithTag("history-group-toggle").performClick()
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.ok)).performClick()
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        // Window-level dialog dismissal is animated outside the Compose test clock.
        android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
