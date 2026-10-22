package com.futo.platformplayer.compose.onboarding

import android.content.Context
import com.futo.platformplayer.compose.ResilientPreferences
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.data.ProfileRepository
import com.futo.platformplayer.compose.data.SharedPreferencesSourceRepository
import com.futo.platformplayer.compose.ui.GrayjayUiState

/** Installation-wide presentation choices; account data remains isolated in its own profile. */
internal class GuidedSetupPreferences(context: Context) {
    private val app = context.applicationContext
    private val prefs = ResilientPreferences(app.getSharedPreferences("grayjoy_guided_setup", Context.MODE_PRIVATE))
    init {
        // Suspend the feature without deleting the profiles or libraries created previously.
        if (!SIMPLIFIED_PROFILES_ENABLED && prefs.getBoolean("simple_startup", false)) {
            prefs.edit().putBoolean("simple_startup", false).apply()
        }
    }
    var showOnStartup: Boolean
        get() = prefs.getBoolean("show_on_startup", true)
        set(value) { prefs.edit().putBoolean("show_on_startup", value).apply() }
    var simpleStartup: Boolean
        get() = SIMPLIFIED_PROFILES_ENABLED && prefs.getBoolean("simple_startup", false)
        set(value) { prefs.edit().putBoolean("simple_startup", value && SIMPLIFIED_PROFILES_ENABLED).apply() }
    fun profileId(source: String): String? = prefs.getString("profile_$source", null)

    /** Called off the UI thread. Save each owned ID immediately so interrupted setup is idempotent. */
    fun ensureProfiles(state: GrayjayUiState): Map<String, String> = synchronized(lock) {
        check(SIMPLIFIED_PROFILES_ENABLED) { "Simplified profiles are temporarily disabled" }
        val catalog = ProfileRepository(app)
        listOf("youtube" to "YouTube", "spotify" to "Spotify").associate { (source, name) ->
            val existing = profileId(source)?.takeIf { id -> catalog.profiles().any { it.id == id } }
            val id = existing ?: catalog.createProfile(name).id.also { created ->
                check(prefs.edit().putString("profile_$source", created).commit())
                GrayjayPreferences(app, created).apply {
                    themeMode = state.themeMode
                    dynamicColorsEnabled = state.dynamicColorsEnabled
                    brainrotShortsEnabled = state.brainrotShortsEnabled
                    holdToSpeedEnabled = state.holdToSpeedEnabled
                    sponsorBlockEnabled = state.sponsorBlockEnabled
                    sponsorBlockAutomaticSkip = state.sponsorBlockAutomaticSkip
                    youtubeAutoplayEnabled = state.youtubeAutoplayEnabled
                }
            }
            // A new profile starts with only its intended source. Do not overwrite choices in
            // an existing profile when reopening the guide or re-enabling the startup chooser.
            if (existing == null) SharedPreferencesSourceRepository(app, id).setEnabled(source, true)
            source to id
        }
    }
    companion object {
        const val SIMPLIFIED_PROFILES_ENABLED = false
        private val lock = Any()
    }
}
