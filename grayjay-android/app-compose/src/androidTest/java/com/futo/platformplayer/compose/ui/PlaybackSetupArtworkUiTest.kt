package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlaybackSetupArtworkUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var dark by mutableStateOf(false)
    private var state by mutableStateOf(GrayjayUiState(
        sources = listOf(SourceUiModel("youtube", "YouTube", "", 0, true))))
    private fun show(step: SetupStep, actions: GrayjayAppActions = GrayjayAppActions()) {
        rule.mainClock.autoAdvance = false
        val page = guidedSetupSteps(false, false).indexOf(step)
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = dark) {
            GuidedSetupDialog(state, actions, page, {}, {}, {})
        } }
        rule.mainClock.advanceTimeBy(100)
    }
    @Test fun normalizationDemoAnimatesWithoutEnablingDsp() {
        var changes = 0
        show(SetupStep.AudioNormalization, GrayjayAppActions().apply {
            onAudioNormalizationChange = { changes++; state = state.copy(audioNormalizationEnabled = it) }
        })
        assertAnimation("setup-normalization-art", 1600)
        capture("setup-normalization-light")
        rule.runOnIdle { dark = true }
        rule.mainClock.advanceTimeBy(300)
        capture("setup-normalization-dark")
        rule.runOnIdle { assertEquals(0, changes); assertFalse(state.audioNormalizationEnabled) }
        rule.mainClock.autoAdvance = true
        rule.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertEquals(1, changes); assertTrue(state.audioNormalizationEnabled) }
    }
    @Test fun linkingIllustrationNeverSignsInWithoutExplicitClick() {
        var logins = 0
        show(SetupStep.YoutubeLogin, GrayjayAppActions().apply { onLoginSource = { logins++ } })
        assertAnimation("setup-youtube-account-art", 3000)
        capture("setup-youtube-account-light")
        rule.runOnIdle { dark = true }
        rule.mainClock.advanceTimeBy(300)
        capture("setup-youtube-account-dark")
        assertEquals(0, logins)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText(rule.activity.getString(R.string.setup_sign_in_youtube)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, logins) }
    }
    @Test fun autoplayDemonstrationLeavesPlaybackPreferenceUntouched() {
        var changes = 0
        show(SetupStep.Autoplay, GrayjayAppActions().apply {
            onYoutubeAutoplayChange = { changes++; state = state.copy(youtubeAutoplayEnabled = it) }
        })
        assertAnimation("setup-youtube-autoplay-art", 4000)
        capture("setup-autoplay-light")
        rule.runOnIdle { dark = true }
        rule.mainClock.advanceTimeBy(300)
        capture("setup-autoplay-dark")
        rule.runOnIdle { assertEquals(0, changes) }
        rule.mainClock.autoAdvance = true
        rule.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertEquals(1, changes) }
    }
    @Test fun libraryDemoRaisesTheMenuFromTheNavbar() {
        show(SetupStep.LibraryShortcuts)
        capture("setup-library-menu-closed")
        rule.mainClock.advanceTimeBy(900)
        capture("setup-library-menu-press")
        assertAnimation("setup-library-shortcuts-art", 2300)
        capture("setup-library-menu-open")
        rule.runOnIdle { dark = true }
        rule.mainClock.advanceTimeBy(300)
        capture("setup-library-menu-dark")
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText(rule.activity.getString(R.string.library_shortcuts_description))
            .performScrollTo().assertIsDisplayed()
    }
    private fun assertAnimation(tag: String, millis: Long) {
        rule.onNodeWithTag(tag).assertIsDisplayed()
        val before = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(millis)
        val after = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height step 4) for (x in 0 until before.width step 4)
            if (before[x, y] != after[x, y]) changed++
        assertTrue("The illustration must animate: $tag", changed > 20)
    }
    private fun capture(name: String) {
        val bitmap = rule.onNodeWithTag("guided-setup").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
