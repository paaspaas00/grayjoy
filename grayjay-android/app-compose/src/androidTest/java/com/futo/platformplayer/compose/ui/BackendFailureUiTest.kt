package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.errors.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class BackendFailureUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun youtubeChallengeShowsFriendlyTitleAndNoBackendDump() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val kind = classifyBackendFailure(IllegalStateException("Both YouTube playback engines failed: LOGIN_REQUIRED not a bot"))
        val state = GrayjayUiState(videoOpenDialog = VideoOpenDialogUiModel("fixture", "Housewife",
            rule.activity.getString(kind.body), false, headlineRes = kind.title, showAsDialog = kind.needsAction))
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, GrayjayAppActions()) } }
            rule.onNodeWithText(rule.activity.getString(R.string.friendly_youtube_title)).assertIsDisplayed()
            rule.onAllNodes(hasText("LOGIN_REQUIRED", substring = true)).assertCountEquals(0)
            rule.onAllNodes(hasText("Both YouTube playback engines", substring = true)).assertCountEquals(0)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(instrumentation.targetContext.getExternalFilesDir(null), "friendly-youtube-error.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally { rule.runOnUiThread { player.release() } }
    }
    @Test fun transientFailuresDoNotCreateAModalOrBlockNavigation() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var dismissed = false
        var state by mutableStateOf(GrayjayUiState(videoOpenDialog = VideoOpenDialogUiModel("fixture", "Track",
            rule.activity.getString(R.string.friendly_timeout_body), false, headlineRes = R.string.friendly_timeout_title,
            showAsDialog = false, noticeMessage = rule.activity.getString(R.string.friendly_timeout_title))))
        val actions = GrayjayAppActions().apply { onDismissVideoOpenDialog = { dismissed = true; state = state.copy(videoOpenDialog = null) } }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
            rule.waitUntil(5000) { dismissed }
            rule.onAllNodes(isDialog()).assertCountEquals(0)
            rule.onNodeWithTag("nav-library").performClick()
            rule.onNodeWithTag("library-filter-history").assertIsDisplayed()
        } finally { rule.runOnUiThread { player.release() } }
    }

    @Test fun failedHomePaginationWaitsForExplicitRetryInsteadOfLooping() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val video = VideoUiModel("failure-fixture", "Video", "Creator", "", "1:00")
        var requests = 0
        var state by mutableStateOf(GrayjayUiState(videos = listOf(video),
            home = HomeUiState(videos = listOf(video), hasMore = true, continuationId = "fixture"),
            sources = listOf(SourceUiModel("youtube", "YouTube", "", 0L, true))))
        val actions = GrayjayAppActions().apply { onLoadMoreHome = {
            requests++
            state = state.copy(home = state.home.copy(errorMessage = rule.activity.getString(R.string.friendly_network_body)))
        } }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
            rule.waitUntil(5000) { requests == 1 }
            rule.waitForIdle()
            assertEquals(1, requests)
            rule.onAllNodes(isDialog()).assertCountEquals(0)
            rule.onNodeWithTag("home-load-more-retry").performScrollTo().performClick()
            rule.waitForIdle()
            assertEquals(2, requests)
        } finally { rule.runOnUiThread { player.release() } }
    }
}
