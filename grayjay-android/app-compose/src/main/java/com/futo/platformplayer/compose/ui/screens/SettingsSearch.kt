package com.futo.platformplayer.compose.ui.screens

import android.content.res.Resources
import com.futo.platformplayer.compose.R
import java.text.Normalizer
import java.util.Locale

internal fun normalizeSettingsQuery(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('×', 'x').lowercase(Locale.ROOT)

internal fun settingsTextMatches(text: String, query: String): Boolean {
    val normalized = normalizeSettingsQuery(text)
    return normalizeSettingsQuery(query).split(Regex("\\s+")).all { it in normalized }
}

internal val settingsSearchResources = mapOf(
    "audio-normalization" to listOf(R.string.experimental_features, R.string.audio_normalization, R.string.audio_normalization_description),
    "app-language" to listOf(R.string.system_default_language, R.string.app_language, R.string.app_language_description),
    "theme-mode" to listOf(R.string.settings_appearance, R.string.automatic, R.string.use_light_theme, R.string.use_dark_theme),
    "toggle-dynamic-colors" to listOf(R.string.use_wallpaper_colors, R.string.material_you_dynamic_color),
    "brainrot-shorts" to listOf(R.string.brainrot_shorts, R.string.brainrot_shorts_description),
    "default-playback-speed" to listOf(R.string.default_playback_speed),
    "youtube-autoplay" to listOf(R.string.youtube_autoplay, R.string.youtube_autoplay_description),
    "per-channel-playback-speed" to listOf(R.string.per_channel_playback_speed, R.string.per_channel_playback_speed_description),
    "hold-to-speed" to listOf(R.string.hold_to_speed, R.string.hold_to_speed_description),
    "sponsorblock" to listOf(R.string.sponsorblock, R.string.on, R.string.off, R.string.setup_sponsor_body, R.string.sponsorblock_automatic_skip, R.string.sponsorblock_manual_description),
    "preferred-video-quality" to listOf(R.string.preferred_quality, R.string.automatic, R.string.quality_maximum),
    "preferred-audio-quality" to listOf(R.string.preferred_audio_quality, R.string.high_quality, R.string.low_data),
    "preferred-audio-language" to listOf(R.string.primary_audio_language),
    "prefer-original-audio" to listOf(R.string.prefer_original_audio, R.string.prefer_original_audio_description),
    "prefer-newpipe-youtube-playback" to listOf(R.string.youtube_backend, R.string.newpipe_backend, R.string.grayjay_plugin_backend),
    "subscription-fetch-mode" to listOf(R.string.subscription_fetch_mode, R.string.subscription_fetch_fast, R.string.subscription_fetch_complete),
    "video-title-language" to listOf(R.string.video_title_language, R.string.prefer_original_title, R.string.use_app_language_for_titles),
    "sticky-captions" to listOf(R.string.remember_subtitles, R.string.remember_subtitles_description),
    "keep-screen-awake" to listOf(R.string.keep_screen_awake, R.string.keep_screen_awake_description),
    "picture-in-picture" to listOf(R.string.picture_in_picture, R.string.picture_in_picture_description),
    "automatic-playlist-downloads" to listOf(R.string.automatic_playlist_downloads, R.string.automatic_playlist_downloads_description),
    "other-audio-ducking" to listOf(R.string.lower_volume_for_other_audio, R.string.lower_volume_for_other_audio_description),
    "other-audio-duck-volume" to listOf(R.string.reduced_playback_volume, R.string.reduced_playback_volume_value),
    "show-recommendations" to listOf(R.string.show_recommendations, R.string.show_recommendations_description),
    "search-history" to listOf(R.string.search_history, R.string.search_history_description),
    "toggle-private-session" to listOf(R.string.private_session, R.string.private_session_description),
    "privacy-controls" to listOf(R.string.privacy_controls, R.string.privacy_controls_description),
    "crash-logging" to listOf(R.string.save_crash_logs, R.string.save_crash_logs_description),
    "share-diagnostics" to listOf(R.string.share_diagnostics),
    "manage-sources" to listOf(R.string.manage_sources),
    "paired-computers" to listOf(R.string.paired_computers, R.string.paired_computers_count),
    "import-grayjay-database" to listOf(R.string.import_grayjay_database, R.string.import_grayjay_database_description),
    "import-newpipe-database" to listOf(R.string.import_newpipe_database, R.string.import_newpipe_database_description),
    "storage-management" to listOf(R.string.storage_management, R.string.storage_management_description),
    "export-grayjay-data" to listOf(R.string.export_grayjay_data, R.string.export_grayjay_data_description),
    "export-newpipe-data" to listOf(R.string.export_newpipe_data, R.string.export_newpipe_data_description),
    "advanced-preferences" to listOf(R.string.advanced_settings, R.string.advanced_settings_description, R.string.rebuild_content_caches, R.string.engine_versions, R.string.plugin_versions),
    "voice-preferences" to listOf(R.string.voice_control, R.string.voice_control_description),
    "guided-setup" to listOf(R.string.setup_title, R.string.setup_reopen),
)

internal fun matchingSettings(resources: Resources, query: String): Set<String> =
    settingsSearchResources.filter { (_, ids) ->
        settingsTextMatches(ids.joinToString(" ") { resources.getString(it) }, query)
    }.keys
