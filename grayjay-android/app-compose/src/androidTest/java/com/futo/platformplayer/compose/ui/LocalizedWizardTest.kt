package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.content.res.Configuration
import android.graphics.Bitmap
import java.io.File
import java.util.Locale
import com.futo.platformplayer.compose.AppLanguageManager
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.GuidedSetupDialog
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Rule
import org.junit.Test

class LocalizedWizardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun tourControlsRemainUsableInEverySupportedLanguage() {
        val original = Configuration(rule.activity.resources.configuration)
        val originalApp = Configuration(rule.activity.applicationContext.resources.configuration)
        var language by mutableStateOf("")
        rule.setContent {
            val config = remember(language) { Configuration(rule.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            } }
            val context = remember(language) { rule.activity.createConfigurationContext(config) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                GrayjayTheme(dynamicColor = false) {
                    key(language) {
                    GuidedSetupDialog(GrayjayUiState(), GrayjayAppActions(), 1, {}, {}, { it() })
                    }
                }
            }
        }
        try { AppLanguageManager.supportedLanguages.forEach { locale ->
            rule.runOnIdle {
                val activeConfig = Configuration(original).apply { setLocale(Locale.forLanguageTag(locale.tag)) }
                // A Dialog owns an Android window and resolves resources through that window's
                // context, not the outer test CompositionLocal. Change only this test process.
                @Suppress("DEPRECATION")
                rule.activity.resources.updateConfiguration(activeConfig, rule.activity.resources.displayMetrics)
                @Suppress("DEPRECATION")
                rule.activity.applicationContext.resources.updateConfiguration(activeConfig, rule.activity.resources.displayMetrics)
                language = locale.tag
            }
            rule.onNodeWithText(rule.activity.getString(R.string.setup_title)).assertIsDisplayed()
            rule.onNodeWithTag("setup-tour-navigation").assertIsDisplayed()
            rule.onNodeWithTag("setup-next").assertIsDisplayed()
            rule.onNodeWithTag("setup-back").assertIsDisplayed()
            rule.onNodeWithTag("setup-tour-search").performClick()
            rule.onNodeWithTag("setup-tour-description").assertIsDisplayed()
            if (locale.tag in setOf("it", "ja", "ar", "de")) {
                rule.waitForIdle()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    File(instrumentation.targetContext.getExternalFilesDir(null), "wizard-${locale.tag}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } } finally {
            rule.runOnIdle {
                @Suppress("DEPRECATION")
                rule.activity.resources.updateConfiguration(original, rule.activity.resources.displayMetrics)
                @Suppress("DEPRECATION")
                rule.activity.applicationContext.resources.updateConfiguration(originalApp, rule.activity.resources.displayMetrics)
            }
        }
    }
}
