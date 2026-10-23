package com.futo.platformplayer.compose.ui

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
import android.graphics.Bitmap
import java.io.File
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HelpGuideUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun accordionHasOnlyOneExpandedTopicAndStartsTheSelectedGuide() {
        val guide = HelpGuideState().apply { visible = true }
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            if (guide.visible) HelpScreen(guide)
        } }
        rule.onNodeWithTag("help-topic-Playlist").performClick()
        rule.onNodeWithTag("help-start-Playlist").assertIsDisplayed()
        rule.onNodeWithTag("help-topic-Music").performClick()
        rule.onNodeWithTag("help-start-Playlist").assertDoesNotExist()
        rule.onNodeWithTag("help-start-Music").performClick()
        rule.runOnIdle {
            assertFalse(guide.visible)
            assertEquals(HelpTopic.Music, guide.topic)
            assertEquals(0, guide.step)
        }
    }

    @Test fun tourNavigationStaysAtBottomAndChangesOnlyItsExplanation() {
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(GrayjayUiState(), GrayjayAppActions(), 1, {}, {}, { it() })
        } }
        val nav = rule.onNodeWithTag("setup-tour-navigation")
        nav.assertIsDisplayed()
        rule.onNodeWithTag("setup-tour-search").performClick()
        rule.onNodeWithTag("setup-tour-description").assertTextEquals(rule.activity.getString(R.string.setup_tour_search))
        rule.onNodeWithTag("setup-page-tour").assertIsDisplayed()
        assertTrue(nav.fetchSemanticsNode().boundsInRoot.bottom <
            rule.onNodeWithTag("setup-next").fetchSemanticsNode().boundsInRoot.top)
        rule.onNodeWithTag("setup-back").performClick()
        rule.onNodeWithTag("welcome-grayjoy-logo").assertIsDisplayed()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-tour-description").assertTextEquals(rule.activity.getString(R.string.setup_tour_search))
    }

    @Test fun bubbleDoesNotConsumeOutsideTapsAndRequiresTheStepToBeReady() {
        val guide = HelpGuideState().apply { start(HelpTopic.Music) }
        var ready by mutableStateOf(false)
        var query by mutableStateOf("")
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalHelpGuide provides guide) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    OutlinedTextField(query, { query = it }, modifier =
                        Modifier.fillMaxWidth().testTag("actual-input").helpAnchor("search-field"))
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { ready = true }, modifier = Modifier.testTag("actual-action")) { Text("Actual action") }
                }
                HelpBubble(guide, ready)
            }
        } }
        rule.onNodeWithTag("help-next").assertIsNotEnabled()
        rule.onNodeWithTag("actual-input").performTextInput("music")
        rule.runOnIdle { assertEquals("music", query) }
        rule.onNodeWithTag("actual-action").performClick()
        rule.onNodeWithTag("help-next").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, guide.step) }
        rule.onNodeWithTag("help-stop").performClick()
        rule.onNodeWithTag("help-bubble").assertDoesNotExist()
    }

    @Test fun playlistGuideCanContinueAboveTheActionSheetAndCreationDialog() {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val video = VideoUiModel("help-video", "A song", "An artist", "3:00", "", sourceId = "youtube")
        var createdName = ""
        val state = GrayjayUiState(
            sources = listOf(SourceUiModel("youtube", "YouTube", "", 0, true)),
            playlists = (1..30).map { PlaylistUiModel("guide-$it", "Playlist $it", "", emptyList()) },
            search = SearchUiState(query = "song", hasSearched = true, videos = listOf(video)))
        val actions = GrayjayAppActions().apply { onCreatePlaylist = { title, _ -> createdName = title } }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
            rule.onNodeWithTag("open-help").performClick()
            rule.onNodeWithTag("help-topic-Playlist").performClick()
            rule.onNodeWithTag("help-start-Playlist").performClick()
            rule.onNodeWithTag("search-field").assertIsDisplayed()
            rule.onNodeWithTag("help-next").assertIsEnabled().performClick()
            rule.onNodeWithTag("video-card-help-video").performTouchInput { longClick() }
            screenshot("help-sheet-final")
            rule.onNodeWithText(rule.activity.getString(R.string.help_step, 3, 4)).assertIsDisplayed()
            rule.onNodeWithText(rule.activity.getString(R.string.help_add_playlist)).assertIsDisplayed()
            rule.onNodeWithTag("video-action-playlist").performScrollTo().performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.help_step, 4, 4)).assertIsDisplayed()
            screenshot("help-playlists-final")
            rule.onNodeWithText(rule.activity.getString(R.string.new_playlist)).performTouchInput { click() }
            rule.onNodeWithTag("new-playlist-name").performTouchInput { click() }
            rule.onNodeWithTag("new-playlist-name").performTextInput("My test playlist")
            screenshot("help-playlist-name-final")
            rule.onNodeWithTag("create-playlist").performClick()
            rule.runOnIdle { assertEquals("My test playlist", createdName) }
            rule.onNodeWithTag("help-next").performClick()
            rule.onNodeWithTag("help-bubble").assertDoesNotExist()
        } finally { rule.runOnUiThread { player.release() } }
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("helpScreenshots") != "true") return
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
