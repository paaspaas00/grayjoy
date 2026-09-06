package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerMorphViewportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun translatedViewportStaysOpaqueThroughoutBothDirectionsWithoutRemeasuringContent() {
        val progress = mutableFloatStateOf(1f)
        val active = mutableStateOf(false)
        var measureCount = 0
        composeRule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(300.dp, 600.dp).background(Color.Green).testTag("morph-root")) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .playerMorphViewport(
                            expandedHeightPx = with(density) { 600.dp.toPx() },
                            minimizedHeightPx = with(density) { 72.dp.toPx() },
                            minimizedTopPx = with(density) { 500.dp.toPx() },
                            isActive = active.value,
                            progress = { progress.floatValue },
                        ),
                ) {
                    Box(
                        Modifier
                            .layout { measurable, constraints ->
                                measureCount++
                                val content = measurable.measure(constraints)
                                layout(content.width, content.height) { content.place(0, 0) }
                            }
                            .fillMaxSize()
                            .background(Color.Red),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val hidden = composeRule.onNodeWithTag("morph-root").captureToImage().toPixelMap()
        assertEquals(Color.Green, hidden[hidden.width / 2, hidden.height / 2])
        composeRule.runOnIdle { active.value = true }
        composeRule.waitForIdle()
        val initialMeasureCount = measureCount

        for (value in listOf(1f, 0.9f, 0.65f, 0.35f, 0f, 0.35f, 0.65f, 0.9f, 1f)) {
            composeRule.runOnIdle {
                progress.floatValue = value
            }
            val pixels = composeRule.onNodeWithTag("morph-root").captureToImage().toPixelMap()
            val top = 500f * value
            val height = transitionOverlayHeightPx(600f, 72f, value)
            val sampleY = ((top + height / 2f) / 600f * pixels.height).toInt()
            assertEquals("Opaque moving viewport at progress $value", Color.Red,
                pixels[pixels.width * 9 / 10, sampleY])
            if (top > 10f) {
                val aboveY = ((top - 5f) / 600f * pixels.height).toInt()
                assertEquals("No painting above the viewport at progress $value", Color.Green,
                    pixels[pixels.width / 2, aboveY])
            }
        }
        composeRule.runOnIdle { assertEquals(initialMeasureCount, measureCount) }
        composeRule.runOnIdle { active.value = false }
        val dismissed = composeRule.onNodeWithTag("morph-root").captureToImage().toPixelMap()
        assertEquals(Color.Green, dismissed[dismissed.width / 2, dismissed.height * 9 / 10])
    }
}
