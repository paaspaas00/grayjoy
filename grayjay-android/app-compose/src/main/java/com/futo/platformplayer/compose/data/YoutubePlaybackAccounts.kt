package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.ui.ProfileProtection
import com.futo.platformplayer.compose.ui.ProfileUiModel

internal const val YOUTUBE_ACCOUNT_PLUGIN_ID = "35ae969a-a7db-11ed-afa1-0242ac120002"

internal fun canShareYoutubePlaybackAccount(consumer: ProfileUiModel?, owner: ProfileUiModel?, spotifyOnly: Boolean = false): Boolean =
    consumer != null && owner != null && consumer.id != owner.id &&
        consumer.id != "private" && owner.id != "private" &&
        consumer.protection == ProfileProtection.None && owner.protection == ProfileProtection.None &&
        (spotifyOnly || consumer.sourceId == "spotify" || consumer.name.equals("spotify", true))

/** Stores only an explicit profile reference. Sessions remain in their original profile. */
internal class YoutubePlaybackAccounts(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("youtube_playback_account_links", Context.MODE_PRIVATE)
    private val profiles by lazy { ProfileRepository(app) }

    class Session(val consumer: String, val owner: String, val auth: SourceAuth) {
        fun sameAs(other: Session?): Boolean = other != null && consumer == other.consumer && owner == other.owner && auth == other.auth
        override fun toString(): String = "YouTube playback session (redacted)"
    }

    fun linkedProfile(consumer: String): String? = runCatching { preferences.getString(consumer, null) }.getOrNull()

    fun candidates(consumer: String): List<ProfileUiModel> {
        val visible = profiles.profiles()
        val current = visible.firstOrNull { it.id == consumer }
        val spotifyOnly = SharedPreferencesSourceRepository(app, consumer).storedEnabledSourceIds() == setOf("spotify")
        return visible.filter { canShareYoutubePlaybackAccount(current, it, spotifyOnly) &&
            !GrayjayPreferences(app, it.id).privateSessionEnabled &&
            GrayjayPluginAuthStore.has(app, it.id, YOUTUBE_ACCOUNT_PLUGIN_ID) }
    }

    fun link(consumer: String, owner: String?) {
        require(profiles.activeProfileId() == consumer) { "The active profile changed." }
        require(owner == null || candidates(consumer).any { it.id == owner }) { "This account is no longer available." }
        check(preferences.edit().apply { if (owner == null) remove(consumer) else putString(consumer, owner) }.commit())
    }

    fun session(consumer: String): Session? {
        val visible = profiles.profiles()
        val current = visible.firstOrNull { it.id == consumer } ?: return null
        if (GrayjayPreferences(app, consumer).privateSessionEnabled) return null
        val owner = linkedProfile(consumer) ?: return GrayjayPluginAuthStore.load(app, consumer, YOUTUBE_ACCOUNT_PLUGIN_ID)
            ?.let { Session(consumer, consumer, it) }
        val spotifyOnly = SharedPreferencesSourceRepository(app, consumer).storedEnabledSourceIds() == setOf("spotify")
        if (!canShareYoutubePlaybackAccount(current, visible.firstOrNull { it.id == owner }, spotifyOnly) ||
            GrayjayPreferences(app, owner).privateSessionEnabled) return null
        return GrayjayPluginAuthStore.load(app, owner, YOUTUBE_ACCOUNT_PLUGIN_ID)?.let { Session(consumer, owner, it) }
    }
}
