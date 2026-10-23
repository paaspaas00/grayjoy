package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileSwipeGestureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val rows = listOf(
        ProfileUiModel("one", "YouTube", ProfileProtection.None, sourceId = "youtube"),
        ProfileUiModel("locked", "Locked", ProfileProtection.Pin),
        ProfileUiModel("two", "Spotify", ProfileProtection.None, sourceId = "spotify"),
        ProfileUiModel("three", "Other", ProfileProtection.None),
        ProfileUiModel("private", "Private", ProfileProtection.DeviceCredential),
    )
    private val changes = mutableListOf<String>()
    private var opened = 0
    private var nav by mutableStateOf(ProfileNavigation())
    private val dp get() = rule.activity.resources.displayMetrics.density

    private fun launch(async: Boolean = false, profiles: List<ProfileUiModel> = rows) {
        nav = ProfileNavigation(profiles, "one", switch = { id ->
            changes += id
            nav = if (async) nav.copy(enabled = false) else nav.copy(current = id)
        })
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalProfileNavigation provides nav) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProfileMenuButton { opened++ } }
            }
        } }
    }

    @Test fun bothDirectionsWrapSkipProtectionAndKeepTapSeparate() {
        launch()
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeUp() }
        rule.runOnIdle { assertEquals("two", nav.current) }
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeDown() }
        rule.runOnIdle { assertEquals("one", nav.current) }
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeDown() }
        rule.runOnIdle { assertEquals("three", nav.current) }
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(listOf("two", "one", "three"), changes); assertEquals(0, opened) }
        rule.onNodeWithTag("profile-menu").performClick()
        rule.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun recognizedSwipeAnimatesOnceAndMetadataRefreshDoesNotInterruptIt() {
        launch()
        rule.mainClock.autoAdvance = false
        val initial = rule.onNodeWithTag("profile-avatar-current", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y
        screenshot("profile-swipe-before")
        rule.onNodeWithTag("profile-menu").performTouchInput {
            down(center); moveBy(Offset(0f, -24f * dp), 250)
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertTrue(changes.isEmpty()); nav = nav.copy(profiles = rows.map { it.copy(name = it.name + "!") }) }
        val dragged = rule.onNodeWithTag("profile-avatar-current", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y
        assertTrue("The outgoing icon moves in the swipe direction", dragged < initial - 2f)
        screenshot("profile-swipe-during")
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle { assertTrue("Do not replace the page/header during the incoming animation", changes.isEmpty()) }
        val incomingSize = rule.onNodeWithTag("profile-avatar-current", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(260)
        val completedSize = rule.onNodeWithTag("profile-avatar-current", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertTrue("The incoming icon must animate before the header changes", incomingSize < completedSize)
        rule.onNodeWithTag("profile-menu").performTouchInput { moveBy(Offset(0f, 70f * dp), 150); up() }
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { assertEquals(listOf("two"), changes) }
        screenshot("profile-swipe-after")
    }

    @Test fun shortGestureAndCancelledJitterDoNotSwitchOrOpen() {
        launch()
        rule.onNodeWithTag("profile-menu").performTouchInput {
            down(center); moveBy(Offset(0f, -12f * dp), 350); advanceEventTime(180); up()
        }
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertEquals(0, opened) }
        rule.onNodeWithTag("profile-menu").performTouchInput {
            down(center); moveBy(Offset(0f, -12f * dp), 300); cancel()
        }
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertEquals("one", nav.current) }
        rule.onNodeWithTag("profile-menu").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun reversingAfterRecognitionCannotTriggerASecondSwitch() {
        launch()
        rule.onNodeWithTag("profile-menu").performTouchInput {
            down(center); moveBy(Offset(0f, -60f * dp), 250)
            moveBy(Offset(0f, 120f * dp), 350); up()
        }
        rule.runOnIdle { assertEquals(listOf("two"), changes) }
    }

    @Test fun pendingSwitchRejectsDuplicateGesturesAndRecoversAfterAcknowledgement() {
        launch(async = true)
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeUp() }
        rule.runOnIdle { assertEquals(listOf("two"), changes) }
        rule.onNodeWithTag("profile-menu").assertIsNotEnabled().performTouchInput { swipeUp() }
        rule.runOnIdle { assertEquals(1, changes.size); nav = nav.copy(current = "two", enabled = true) }
        rule.onNodeWithTag("profile-menu").assertIsEnabled().performTouchInput { swipeUp() }
        rule.runOnIdle { assertEquals(listOf("two", "three"), changes) }
    }

    @Test fun changedProtectionOrExternalProfileSwitchCannotCommitAStaleTarget() {
        launch()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("profile-menu").performTouchInput { down(center); moveBy(Offset(0f, -60f * dp), 300) }
        rule.runOnIdle { nav = nav.copy(profiles = rows.map { if (it.id == "two") it.copy(protection = ProfileProtection.Pin) else it }) }
        rule.onNodeWithTag("profile-menu").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertTrue(changes.isEmpty()); nav = nav.copy(profiles = rows) }
        rule.onNodeWithTag("profile-menu").performTouchInput { down(center); moveBy(Offset(0f, -60f * dp), 300) }
        rule.runOnIdle { nav = nav.copy(current = "three") }
        rule.onNodeWithTag("profile-menu").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(400)
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertEquals("three", nav.current) }
    }

    @Test fun aSingleAvailableProfileDoesNotPretendToSwitch() {
        launch(profiles = listOf(rows[0], rows[1], rows[4]))
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeUp() }
        rule.onNodeWithTag("profile-menu").performTouchInput { swipeDown() }
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertEquals("one", nav.current) }
        rule.onNodeWithTag("profile-menu").performClick()
        rule.runOnIdle { assertEquals(1, opened) }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
