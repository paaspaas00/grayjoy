package com.futo.platformplayer.compose.onboarding

import android.content.Context
import com.futo.platformplayer.compose.ResilientPreferences
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.data.ProfileRepository
import com.futo.platformplayer.compose.data.SharedPreferencesSourceRepository
import com.futo.platformplayer.compose.ui.GrayjayUiState
import com.futo.platformplayer.compose.BuildConfig

internal const val VERSION_6_GUIDE_CODE = 6000
internal const val VERSION_6_GUIDE_MAX_PREVIOUS = 5000

internal fun needsVersion6UpgradeGuide(
    currentVersion: Int,
    previouslyLaunchedVersion: Int,
    isUpdatedInstallation: Boolean,
): Boolean = currentVersion == VERSION_6_GUIDE_CODE &&
    previouslyLaunchedVersion != currentVersion &&
    (previouslyLaunchedVersion in 1..VERSION_6_GUIDE_MAX_PREVIOUS ||
        (previouslyLaunchedVersion == 0 && isUpdatedInstallation))

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

    /**
     * Persist the pending migration independently from the current launch intent. An update first
     * opened through a shared link must still show the guide on the next ordinary app launch.
     */
    fun requiresVersion6UpgradeGuide(): Boolean = synchronized(lock) {
        if (prefs.getBoolean(KEY_VERSION_6_GUIDE_PENDING, false)) return@synchronized true
        val previous = prefs.getInt(KEY_LAST_LAUNCHED_VERSION, 0).coerceAtLeast(0)
        val packageInfo = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0)
        }.getOrNull()
        val updatedInstallation = packageInfo != null &&
            packageInfo.lastUpdateTime > packageInfo.firstInstallTime
        val required = needsVersion6UpgradeGuide(
            currentVersion = BuildConfig.VERSION_CODE,
            previouslyLaunchedVersion = previous,
            isUpdatedInstallation = updatedInstallation,
        )
        val edit = prefs.edit().putInt(KEY_LAST_LAUNCHED_VERSION, BuildConfig.VERSION_CODE)
        if (required) edit.putBoolean(KEY_VERSION_6_GUIDE_PENDING, true)
        edit.commit()
        required
    }

    /** Mark it as soon as it is eligible to be drawn; process death must not repeat it. */
    fun markVersion6UpgradeGuidePresented() = synchronized(lock) {
        prefs.edit().remove(KEY_VERSION_6_GUIDE_PENDING)
            .putBoolean("show_on_startup", false)
            .putInt(KEY_LAST_LAUNCHED_VERSION, BuildConfig.VERSION_CODE).commit()
    }
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
        private const val KEY_LAST_LAUNCHED_VERSION = "last_launched_version"
        private const val KEY_VERSION_6_GUIDE_PENDING = "version_6_guide_pending"
    }
}
