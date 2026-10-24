package com.futo.platformplayer.compose.ui

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.BuildConfig
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class ExperimentalAudioSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    @Test fun experimentalToggleDefaultsOffAndRepositoryIsImmediatelyAboveVersion() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var state by mutableStateOf(GrayjayUiState())
        var openedUrl: String? = null
        val context = object : ContextWrapper(rule.activity) {
            override fun startActivity(intent: Intent) { openedUrl = intent.dataString }
        }
        val actions = GrayjayAppActions().apply {
            onAudioNormalizationChange = { state = state.copy(audioNormalizationEnabled = it) }
        }
        rule.setContent { CompositionLocalProvider(LocalContext provides context) {
            GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) }
        } }
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("audio-normalization"))
        rule.onNodeWithText(context.getString(R.string.experimental_features)).assertIsDisplayed()
        rule.onNodeWithTag("audio-normalization").assertIsOff().performClick().assertIsOn()
        assertTrue(state.audioNormalizationEnabled)
        rule.onNodeWithTag("audio-normalization").performClick().assertIsOff()
        capture("experimental-audio-normalization")
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("version-information"))
        val experimental = rule.onNodeWithText(context.getString(R.string.experimental_features)).fetchSemanticsNode().boundsInRoot
        val voice = rule.onNodeWithTag("voice-control-settings").fetchSemanticsNode().boundsInRoot
        val normalization = rule.onNodeWithTag("audio-normalization").fetchSemanticsNode().boundsInRoot
        val link = rule.onNodeWithTag("repository-link").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Experimental features must be the last preference section", voice.bottom <= experimental.top)
        assertTrue(normalization.bottom <= link.top)
        val versionText = context.getString(R.string.version_information, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        val version = rule.onNodeWithText(versionText, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(link.bottom <= version.top)
        rule.onNodeWithTag("repository-link").performClick()
        assertEquals("https://github.com/paaspaas00/grayjoy", openedUrl)
        capture("preferences-repository-version")
    }

    @Test fun updateMessageHasNoHostingProviderInAnySupportedTranslation() {
        for (tag in listOf("en", "it", "de", "es", "fr", "pt", "tr", "ru", "ar", "ja", "ko", "zh")) {
            val configuration = android.content.res.Configuration(rule.activity.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            val text = rule.activity.createConfigurationContext(configuration).getString(R.string.update_available_description, "4.3.0")
            assertFalse(text.contains("github", ignoreCase = true))
            assertTrue(text.contains("4.3.0"))
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
