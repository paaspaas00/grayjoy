package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.LocalGuidedSetup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.compose.data.ProfileRepository
import com.futo.platformplayer.compose.onboarding.GuidedSetupHost
import com.futo.platformplayer.compose.onboarding.GuidedSetupPreferences
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuidedStartupTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun oldStartupChoiceIsDisabledAndReopeningAlwaysStartsAtWelcome() {
        val base = rule.activity
        val prefix = "startup-test-${UUID.randomUUID()}"
        val names = mutableSetOf<String>()
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val scoped = "$prefix-$name"; names += scoped
                return base.getSharedPreferences(scoped, mode)
            }
        }
        try {
            context.getSharedPreferences("grayjoy_guided_setup", Context.MODE_PRIVATE).edit()
                .putBoolean("simple_startup", true).putBoolean("show_on_startup", false).commit()
            val preferences = GuidedSetupPreferences(context)
            val profilesBefore = ProfileRepository(context).profiles()
            val state = mutableStateOf(GrayjayUiState(profiles = ProfileRepository(context).profiles()))
            val launcher = mutableIntStateOf(0)
            val external = mutableIntStateOf(0)
            var selected: String? = null
            val actions = GrayjayAppActions().apply { onSwitchProfile = { selected = it; state.value = state.value.copy(activeProfileId = it) } }
            rule.setContent { GrayjayTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalContext provides context) {
                    GuidedSetupHost(state.value, actions, true, launcher.intValue, external.intValue) {
                        val guide = LocalGuidedSetup.current
                        Button(onClick = guide.open) { Text("Open guide") }
                    }
                }
            } }
            rule.onNodeWithTag("startup-youtube").assertDoesNotExist()
            rule.onNodeWithTag("startup-spotify").assertDoesNotExist()
            rule.runOnIdle { launcher.intValue++ }
            rule.onNodeWithTag("startup-youtube").assertDoesNotExist()
            rule.onNodeWithText("Open guide").performClick()
            rule.onNodeWithTag("setup-page-welcome").assertIsDisplayed()
            repeat(3) { rule.onNodeWithTag("setup-next").performClick() }
            rule.onNodeWithTag("setup-page-youtube").assertIsDisplayed()
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.close)).performClick()
            rule.onNodeWithTag("guided-setup").assertDoesNotExist()
            rule.onNodeWithText("Open guide").performClick()
            rule.onNodeWithTag("setup-page-welcome").assertIsDisplayed()
            rule.onNodeWithTag("setup-next").performClick()
            rule.runOnIdle { external.intValue++ }
            rule.onNodeWithTag("guided-setup").assertDoesNotExist()
            rule.onNodeWithText("Open guide").performClick()
            rule.onNodeWithTag("setup-page-welcome").assertIsDisplayed()
            rule.runOnIdle {
                assertNull(selected)
                assertFalse(preferences.simpleStartup)
                assertEquals(profilesBefore, ProfileRepository(context).profiles())
            }
        } finally { names.forEach(base::deleteSharedPreferences); directory.deleteRecursively() }
    }
}
