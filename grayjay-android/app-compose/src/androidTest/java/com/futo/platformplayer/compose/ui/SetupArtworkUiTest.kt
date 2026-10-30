package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SetupArtworkUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun fullWizardKeepsSourceConsentAndDiagnosticsControlsUsable() {
        var configured = false
        val state = GrayjayUiState()
        val actions = GrayjayAppActions().apply { onConfigureSourceProfiles = { configured = true } }
        val page = guidedSetupSteps(false, false).indexOf(SetupStep.SourceProfiles)
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            GuidedSetupDialog(state, actions, page, {}, {}, {})
        } }
        rule.onNodeWithTag("setup-source-profiles-art").assertIsDisplayed()
        capture("setup-source-profiles-page", "guided-setup")
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-crash-bug-art").assertIsDisplayed()
        capture("setup-crash-bug-page", "guided-setup")
        rule.onAllNodes(isToggleable()).onFirst().performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertFalse(configured) }
    }

    @Test fun sourceProfilesAndWalkingBugAnimateWithoutAnyAppActions() {
        var active by mutableStateOf(true)
        var dark by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = dark) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SourceProfilesArtwork(active)
                    CrashLogBugArtwork(active)
                }
            }
        } }
        rule.mainClock.advanceTimeBy(1800)
        rule.onNodeWithText("YouTube").assertIsDisplayed()
        rule.onNodeWithText("Spotify").assertIsDisplayed()
        capture("setup-profile-bug-light")
        val before = rule.onNodeWithTag("setup-crash-bug-art").captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(2500)
        val after = rule.onNodeWithTag("setup-crash-bug-art").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height step 4) for (x in 0 until before.width step 4)
            if (before[x, y] != after[x, y]) changed++
        assertTrue("The insect must move along the row", changed > 20)
        rule.runOnIdle { dark = true }
        rule.mainClock.advanceTimeBy(300)
        capture("setup-profile-bug-dark")
        rule.runOnIdle { active = false }
        rule.mainClock.advanceTimeBy(300)
        val stopped = rule.onNodeWithTag("setup-crash-bug-art").captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(2000)
        val later = rule.onNodeWithTag("setup-crash-bug-art").captureToImage().toPixelMap()
        for (y in 0 until stopped.height step 8) for (x in 0 until stopped.width step 8)
            assertEquals(stopped[x, y], later[x, y])
    }

    @Test fun choicesAreSelectableAndRecognitionActuallyAnimatesInBothThemes() {
        var dark by mutableStateOf(false)
        var left by mutableStateOf(false)
        var active by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            GrayjayTheme(dynamicColor = false, darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        HandednessChoice(left, { left = it }, active)
                        SongRecognitionArtwork(active)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("choose-right-hand").assertIsSelected()
        rule.onNodeWithTag("choose-left-hand").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("choose-left-hand").assertIsSelected()
        capture("setup-artwork-light")
        val before = rule.onNodeWithTag("setup-recognition-art").captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(3400)
        val after = rule.onNodeWithTag("setup-recognition-art").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height step 4) for (x in 0 until before.width step 4) {
            if (before[x, y] != after[x, y]) changed++
        }
        assertTrue("The demo must animate, not just show a static icon", changed > 20)
        capture("setup-artwork-found")
        rule.runOnIdle { dark = true }
        rule.mainClock.advanceTimeBy(300)
        capture("setup-artwork-dark")
        rule.runOnIdle { active = false }
        rule.mainClock.advanceTimeBy(500)
        val paused = rule.onNodeWithTag("setup-recognition-art").captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(1800)
        val later = rule.onNodeWithTag("setup-recognition-art").captureToImage().toPixelMap()
        for (y in 0 until paused.height step 8) for (x in 0 until paused.width step 8) {
            assertEquals(paused[x, y], later[x, y])
        }
    }

    private fun capture(name: String, tag: String? = null) {
        rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        (if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)).captureToImage().asAndroidBitmap().let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
