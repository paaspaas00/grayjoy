package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.ui.screens.settingsGroup
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationPerformanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pageReplacementDisposesOutgoingImmediatelyAndRestoresSavedState() {
        val destination = mutableStateOf("home")
        val active = mutableSetOf<String>()
        var updateCounter: (() -> Unit)? = null
        rule.setContent {
            val holder = rememberSaveableStateHolder()
            QuickPageTransition(destination.value) { page ->
                holder.SaveableStateProvider(page) {
                    val counter = rememberSaveable { mutableStateOf(0) }
                    SideEffect { updateCounter = { counter.value++ } }
                    DisposableEffect(page) {
                        active += page
                        onDispose { active -= page }
                    }
                    Text("$page:${counter.value}")
                }
            }
        }
        rule.runOnIdle { updateCounter!!() }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { destination.value = "prefs" }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { assertEquals(setOf("prefs"), active) }
        rule.runOnUiThread { destination.value = "home" }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText("home:1").assertIsDisplayed()
        rule.runOnIdle { assertEquals(setOf("home"), active) }
    }

    @Test fun settingsGroupOnlyComposesVisibleRowsAndKeepsLastRowReachable() {
        val composed = mutableSetOf<Int>()
        rule.setContent { GrayjayTheme(dynamicColor = false) {
            LazyColumn(Modifier.height(180.dp).testTag("list")) {
                settingsGroup("test", null, 16.dp) {
                    repeat(24) { index ->
                        setting("row-$index", "link") {
                            SideEffect { composed += index }
                            Text("Row $index", Modifier.height(60.dp).testTag("row-$index"))
                        }
                    }
                }
            }
        } }
        rule.runOnIdle { assertTrue("Offscreen settings must stay lazy: $composed", composed.size < 8) }
        rule.onNodeWithTag("list").performScrollToNode(hasTestTag("row-23"))
        rule.onNodeWithTag("row-23").assertIsDisplayed()
        rule.onNodeWithTag("list").performScrollToNode(hasTestTag("row-0"))
        rule.onNodeWithTag("row-0").assertIsDisplayed()
    }
}
