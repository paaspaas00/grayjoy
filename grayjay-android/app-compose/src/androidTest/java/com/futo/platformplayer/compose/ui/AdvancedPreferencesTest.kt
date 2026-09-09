package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.AdvancedPreferencesDialog
import com.futo.platformplayer.compose.ui.screens.ExtraPreferences
import com.futo.platformplayer.compose.ui.screens.LocalExtraPreferences
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AdvancedPreferencesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun clearingRequiresConfirmationAndDismissDoesNotClearAnything() {
        var requests = 0
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalExtraPreferences provides ExtraPreferences(onRebuildCaches = { requests++ })) {
                AdvancedPreferencesDialog(emptyList()) {}
            }
        } }
        rule.onNodeWithTag("rebuild-content-caches").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        rule.runOnIdle { assertEquals(0, requests) }
        rule.onNodeWithTag("rebuild-content-caches").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.ok)).performClick()
        rule.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun relativeDateUsesCurrentClockNotSavedLabel() {
        val published = System.currentTimeMillis() - 60 * 60_000L
        val clock = mutableLongStateOf(published + 60 * 60_000L)
        val video = VideoUiModel("v", "Title", "Creator", "20 views • old label", "1:00", publishedAtMs = published)
        var first = ""
        rule.setContent { val text = refreshedVideoMetadata(video, video.metadata, clock.longValue); SideEffect { if (first.isEmpty()) first = text }; Text(text) }
        rule.waitForIdle()
        rule.runOnIdle { clock.longValue += 48 * 60 * 60_000L }
        rule.onNodeWithText(first).assertDoesNotExist()
        rule.onNodeWithText("old label", substring = true).assertDoesNotExist()
        rule.onNodeWithText("20 views", substring = true).assertIsDisplayed()
    }

    @Test fun relativeDateKeepsViewCountWhenTheSavedMetadataHadNoDate() {
        val published = System.currentTimeMillis() - 2 * 60 * 60_000L
        val video = VideoUiModel(
            id = "views-only",
            title = "Title",
            creator = "Creator",
            metadata = "20 views",
            duration = "1:00",
            viewCount = 20,
            publishedAtMs = published,
        )
        val text = refreshedVideoMetadata(video, video.metadata, System.currentTimeMillis())
        assertTrue(text.startsWith("20 views • "))
    }
}
