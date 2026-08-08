package com.futo.platformplayer.compose

import android.content.Context
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.ThemeMode
import com.futo.platformplayer.compose.engine.OtherAudioDuckingController
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal class GrayjayPreferences(context: Context, profileId: String = "main") {
    private val appContext = context.applicationContext
    private val defaultThemeMode = if (profileId == PRIVATE_PROFILE_ID) {
        ThemeMode.Dark
    } else {
        ThemeMode.System
    }
    private val preferences = context.getSharedPreferences(
        if (profileId == "main") FILE_NAME else "${FILE_NAME}_$profileId",
        Context.MODE_PRIVATE,
    )

    var dynamicColorsEnabled: Boolean
        get() = preferences.getBoolean(KEY_DYNAMIC_COLORS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_DYNAMIC_COLORS, value).apply()
        }

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, defaultThemeMode.name).orEmpty())
        }.getOrDefault(defaultThemeMode)
        set(value) {
            preferences.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    var privateSessionEnabled: Boolean
        get() = preferences.getBoolean(KEY_PRIVATE_SESSION, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PRIVATE_SESSION, value).apply()
        }

    var defaultPlaybackSpeed: Float
        get() = preferences.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1f)
        set(value) {
            preferences.edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, value.coerceIn(0.25f, 3f)).apply()
        }

    var perChannelPlaybackSpeedEnabled: Boolean
        get() = preferences.getBoolean(KEY_PER_CHANNEL_PLAYBACK_SPEED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_PER_CHANNEL_PLAYBACK_SPEED, value).apply()
        }

    var holdToSpeedEnabled: Boolean
        get() = preferences.getBoolean(KEY_HOLD_TO_SPEED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_HOLD_TO_SPEED, value).apply()
        }

    fun channelPlaybackSpeeds(): Map<String, Float> =
        playbackSpeedMap(KEY_CHANNEL_PLAYBACK_SPEEDS)

    fun videoPlaybackSpeeds(): Map<String, Float> =
        playbackSpeedMap(KEY_VIDEO_PLAYBACK_SPEEDS)

    fun setChannelPlaybackSpeed(channelId: String, speed: Float?) {
        setPlaybackSpeedEntry(KEY_CHANNEL_PLAYBACK_SPEEDS, channelId, speed)
    }

    fun setVideoPlaybackSpeed(videoId: String, speed: Float?) {
        setPlaybackSpeedEntry(KEY_VIDEO_PLAYBACK_SPEEDS, videoId, speed)
    }

    var preferredVideoQuality: Int
        get() = preferences.getInt(KEY_PREFERRED_VIDEO_QUALITY, 0)
        set(value) {
            preferences.edit().putInt(KEY_PREFERRED_VIDEO_QUALITY, value.coerceAtLeast(0)).apply()
        }

    /** Target audio bitrate in bits/s. Int.MAX_VALUE means highest available. */
    var preferredAudioBitrate: Int
        get() = preferences.getInt(KEY_PREFERRED_AUDIO_BITRATE, Int.MAX_VALUE)
        set(value) {
            preferences.edit().putInt(
                KEY_PREFERRED_AUDIO_BITRATE,
                value.takeIf { it > 0 } ?: Int.MAX_VALUE,
            ).apply()
        }

    /** Legacy Grayjay's primary audio language. */
    var preferredAudioLanguage: String
        get() = preferences.getString(KEY_PREFERRED_AUDIO_LANGUAGE, "en")
            ?.takeIf(String::isNotBlank)
            ?: "en"
        set(value) {
            preferences.edit()
                .putString(KEY_PREFERRED_AUDIO_LANGUAGE, value.lowercase(Locale.ROOT))
                .apply()
        }

    var preferOriginalAudio: Boolean
        get() = preferences.getBoolean(KEY_PREFER_ORIGINAL_AUDIO, true)
        set(value) {
            preferences.edit().putBoolean(KEY_PREFER_ORIGINAL_AUDIO, value).apply()
        }

    var stickyCaptionsEnabled: Boolean
        get() = preferences.getBoolean(KEY_STICKY_CAPTIONS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_STICKY_CAPTIONS, value).apply()
        }

    var captionsEnabled: Boolean
        get() = preferences.getBoolean(KEY_CAPTIONS_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_CAPTIONS_ENABLED, value).apply()
        }

    var subtitleLanguage: String?
        get() = preferences.getString(KEY_SUBTITLE_LANGUAGE, null)
        set(value) {
            preferences.edit().putString(KEY_SUBTITLE_LANGUAGE, value).apply()
        }

    var showRecommendations: Boolean
        get() = preferences.getBoolean(KEY_SHOW_RECOMMENDATIONS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SHOW_RECOMMENDATIONS, value).apply()
        }

    var searchHistoryEnabled: Boolean
        get() = preferences.getBoolean(KEY_SEARCH_HISTORY_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SEARCH_HISTORY_ENABLED, value).apply()
        }

    var keepScreenAwake: Boolean
        get() = preferences.getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
        set(value) {
            preferences.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, value).apply()
        }

    var pictureInPictureEnabled: Boolean
        get() = preferences.getBoolean(KEY_PICTURE_IN_PICTURE, true)
        set(value) {
            preferences.edit().putBoolean(KEY_PICTURE_IN_PICTURE, value).apply()
        }

    var otherAudioDuckingEnabled: Boolean
        get() = preferences.getBoolean(KEY_OTHER_AUDIO_DUCKING, true)
        set(value) {
            preferences.edit().putBoolean(KEY_OTHER_AUDIO_DUCKING, value).apply()
        }

    var otherAudioDuckVolumePercent: Int
        get() = preferences.getInt(
            KEY_OTHER_AUDIO_DUCK_VOLUME,
            OtherAudioDuckingController.DEFAULT_DUCK_VOLUME_PERCENT,
        ).coerceIn(10, 80)
        set(value) {
            preferences.edit().putInt(KEY_OTHER_AUDIO_DUCK_VOLUME, value.coerceIn(10, 80)).apply()
        }

    fun addSearchHistory(query: String) {
        if (!searchHistoryEnabled || query.isBlank()) return
        val updated = (listOf(query.trim()) + searchHistory())
            .distinctBy(String::lowercase)
            .take(30)
        preferences.edit().putString(KEY_SEARCH_HISTORY, JSONArray(updated).toString()).apply()
    }

    fun searchHistory(): List<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_SEARCH_HISTORY, "[]"))
        buildList {
            for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }.getOrDefault(emptyList())

    fun isCreatorFollowed(creatorId: String): Boolean =
        creatorId.isNotBlank() && creatorId in followedCreatorIds()

    fun setCreatorFollowed(creatorId: String, followed: Boolean) {
        if (creatorId.isBlank()) return
        val updated = followedCreatorIds().toMutableSet()
        if (followed) updated += creatorId else updated -= creatorId
        preferences.edit().putStringSet(KEY_FOLLOWED_CREATORS, updated).apply()
    }

    fun followedCreatorIds(): Set<String> =
        preferences.getStringSet(KEY_FOLLOWED_CREATORS, emptySet()).orEmpty().toSet()

    fun initializeFollowedCreators(defaultCreatorIds: Set<String>): Set<String> {
        if (preferences.getBoolean(KEY_FOLLOWING_INITIALIZED, false)) return followedCreatorIds()
        val initialized = followedCreatorIds() + defaultCreatorIds.filter(String::isNotBlank)
        preferences.edit()
            .putStringSet(KEY_FOLLOWED_CREATORS, initialized)
            .putBoolean(KEY_FOLLOWING_INITIALIZED, true)
            .apply()
        return initialized
    }

    fun mergeImportedSubscriptions(channels: List<ChannelUiModel>) {
        if (channels.isEmpty()) return
        val currentChannels = loadImportedChannels().associateByTo(linkedMapOf(), ChannelUiModel::id)
        channels.forEach { imported ->
            val current = currentChannels[imported.id]
            currentChannels[imported.id] = if (current == null) imported else current.copy(
                name = imported.name.ifBlank { current.name },
                sourceId = imported.sourceId.ifBlank { current.sourceId },
                source = imported.source.ifBlank { current.source },
                followerCount = imported.followerCount.takeUnless { it == "Creator" }
                    ?: current.followerCount,
                description = imported.description.ifBlank { current.description },
                thumbnailUrl = imported.thumbnailUrl.ifBlank { current.thumbnailUrl },
            )
        }
        val followed = followedCreatorIds() + channels.map(ChannelUiModel::id).filter(String::isNotBlank)
        val json = JSONArray().apply {
            currentChannels.values.forEach { channel ->
                put(
                    JSONObject().apply {
                        put("id", channel.id)
                        put("name", channel.name)
                        put("sourceId", channel.sourceId)
                        put("source", channel.source)
                        put("unreadCount", channel.unreadCount)
                        put("followerCount", channel.followerCount)
                        put("description", channel.description)
                        put("thumbnailUrl", channel.thumbnailUrl)
                    },
                )
            }
        }
        preferences.edit()
            .putStringSet(KEY_FOLLOWED_CREATORS, followed)
            .putBoolean(KEY_FOLLOWING_INITIALIZED, true)
            .putString(KEY_IMPORTED_CHANNELS, json.toString())
            .apply()
    }

    fun loadImportedChannels(): List<ChannelUiModel> = runCatching {
        val array = JSONArray(preferences.getString(KEY_IMPORTED_CHANNELS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString("id")
                if (id.isBlank()) continue
                add(
                    ChannelUiModel(
                        id = id,
                        name = json.optString("name", appContext.getString(R.string.imported_creator)),
                        sourceId = json.optString("sourceId", "youtube"),
                        source = json.optString("source"),
                        unreadCount = json.optInt("unreadCount"),
                        followerCount = json.optString("followerCount", appContext.getString(R.string.creator)),
                        description = json.optString("description"),
                        thumbnailUrl = json.optString("thumbnailUrl"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun playbackSpeedMap(key: String): Map<String, Float> = runCatching {
        val json = JSONObject(preferences.getString(key, "{}").orEmpty())
        buildMap {
            json.keys().forEach { id ->
                val speed = json.optDouble(id, Double.NaN).toFloat()
                if (id.isNotBlank() && speed.isFinite()) put(id, speed.coerceIn(0.25f, 3f))
            }
        }
    }.getOrDefault(emptyMap())

    private fun setPlaybackSpeedEntry(key: String, id: String, speed: Float?) {
        if (id.isBlank()) return
        val updated = playbackSpeedMap(key).toMutableMap()
        if (speed == null) updated.remove(id)
        else updated[id] = speed.coerceIn(0.25f, 3f)
        val json = JSONObject().apply {
            updated.forEach { (entryId, entrySpeed) -> put(entryId, entrySpeed.toDouble()) }
        }
        preferences.edit().putString(key, json.toString()).apply()
    }

    companion object {
        internal const val FILE_NAME = "grayjay_compose_preferences"
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PRIVATE_SESSION = "private_session_enabled"
        private const val KEY_FOLLOWED_CREATORS = "followed_creator_ids"
        private const val KEY_FOLLOWING_INITIALIZED = "following_initialized"
        private const val KEY_IMPORTED_CHANNELS = "imported_channels"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        private const val KEY_PER_CHANNEL_PLAYBACK_SPEED = "per_channel_playback_speed_enabled"
        private const val KEY_HOLD_TO_SPEED = "hold_to_speed_enabled"
        private const val KEY_CHANNEL_PLAYBACK_SPEEDS = "channel_playback_speeds"
        private const val KEY_VIDEO_PLAYBACK_SPEEDS = "video_playback_speeds"
        private const val KEY_PREFERRED_VIDEO_QUALITY = "preferred_video_quality"
        private const val KEY_PREFERRED_AUDIO_BITRATE = "preferred_audio_bitrate"
        private const val KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language"
        private const val KEY_PREFER_ORIGINAL_AUDIO = "prefer_original_audio"
        private const val KEY_STICKY_CAPTIONS = "sticky_captions"
        private const val KEY_CAPTIONS_ENABLED = "captions_enabled"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val KEY_SHOW_RECOMMENDATIONS = "show_recommendations"
        private const val KEY_SEARCH_HISTORY_ENABLED = "search_history_enabled"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_PICTURE_IN_PICTURE = "picture_in_picture_enabled"
        private const val KEY_OTHER_AUDIO_DUCKING = "other_audio_ducking_enabled"
        private const val KEY_OTHER_AUDIO_DUCK_VOLUME = "other_audio_duck_volume_percent"
        private const val PRIVATE_PROFILE_ID = "private"
    }
}
