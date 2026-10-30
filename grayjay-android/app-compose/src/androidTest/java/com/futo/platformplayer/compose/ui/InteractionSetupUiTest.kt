package com.futo.platformplayer.compose.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.onboarding.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class InteractionSetupUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun newSetupStepsAreReadableAndOfferRealChoices() {
        var page by mutableIntStateOf(guidedSetupSteps(false, false).indexOf(SetupStep.Handedness))
        var left by mutableStateOf(false)
        var recognition by mutableStateOf(true)
        rule.setContent { GrayjayTheme(dynamicColor = false, darkTheme = false) {
            CompositionLocalProvider(LocalInteractionControls provides InteractionControls(left, recognition, { left = it }, { recognition = it })) {
                GuidedSetupDialog(GrayjayUiState(), GrayjayAppActions(), page, { page = it }, {}, {})
            }
        } }
        rule.onNodeWithTag("choose-left-hand").assertIsDisplayed().performClick()
        rule.runOnIdle { org.junit.Assert.assertTrue(left) }
        capture("interaction-setup-handedness")
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithTag("setup-recognition-art").assertIsDisplayed()
        capture("interaction-setup-recognition")
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.recognition_privacy))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("setup-next").performClick()
        rule.onNodeWithText(rule.activity.getString(com.futo.platformplayer.compose.R.string.library_shortcuts_description)).assertIsDisplayed()
        capture("interaction-setup-shortcuts")
    }
    private fun capture(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        rule.onNodeWithTag("guided-setup").captureToImage().asAndroidBitmap().let { image ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
}
