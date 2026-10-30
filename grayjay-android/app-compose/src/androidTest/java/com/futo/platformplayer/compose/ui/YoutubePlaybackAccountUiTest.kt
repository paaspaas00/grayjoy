package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.compose.data.*
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class YoutubePlaybackAccountUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun linkingRequiresAnExplicitChoiceAndNeverOffersProtectedProfiles() {
        val base = rule.activity
        val prefix = "youtube-link-ui-${UUID.randomUUID()}"
        val names = mutableSetOf<String>()
        val files = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = files
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val key = "$prefix-$name"; synchronized(names) { names += key }; return base.getSharedPreferences(key, mode)
            }
        }
        try {
            val profiles = ProfileRepository(context)
            val owner = profiles.createProfile("YouTube")
            val consumer = profiles.createProfile("Spotify")
            val protected = profiles.createProfile("Protected", "1234")
            val auth = SourceAuth(cookieMap = hashMapOf(".youtube.com" to hashMapOf("SAPISID" to "test-only")))
            GrayjayPluginAuthStore.save(context, owner.id, YOUTUBE_ACCOUNT_PLUGIN_ID, auth)
            GrayjayPluginAuthStore.save(context, protected.id, YOUTUBE_ACCOUNT_PLUGIN_ID, auth)
            profiles.setActiveProfile(consumer.id)
            val links = YoutubePlaybackAccounts(context)
            var notifications = 0
            rule.setContent { CompositionLocalProvider(LocalContext provides context,
                LocalProfileNavigation provides ProfileNavigation(profiles.profiles(), consumer.id),
                LocalYoutubePlaybackAccountChanged provides { notifications++ }) {
                GrayjayTheme(dynamicColor = false) { Surface { YoutubePlaybackAccountPreference() } }
            } }
            rule.waitUntil(5000) { rule.onAllNodes(hasTestTag("youtube-playback-account") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("youtube-playback-account").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTag("youtube-account-profile-${owner.id}").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("youtube-account-profile-${protected.id}").assertDoesNotExist()
            assertNull(links.linkedProfile(consumer.id))
            rule.onNodeWithTag("youtube-account-profile-${owner.id}").performScrollTo().performClick()
            rule.onNodeWithTag("youtube-account-confirm").performClick()
            rule.waitUntil(5000) { links.linkedProfile(consumer.id) == owner.id && notifications == 1 }
            assertEquals(1, notifications)
            assertNull(GrayjayPluginAuthStore.load(context, consumer.id, YOUTUBE_ACCOUNT_PLUGIN_ID))
            assertEquals(auth, GrayjayPluginAuthStore.load(context, owner.id, YOUTUBE_ACCOUNT_PLUGIN_ID))
        } finally { names.forEach(base::deleteSharedPreferences); files.deleteRecursively() }
    }
}
