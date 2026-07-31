package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.BuildConfig
import com.futo.platformplayer.compose.ui.ReleaseUpdateUiModel
import com.futo.platformplayer.compose.ui.PcLinkUiState
import com.futo.platformplayer.compose.ui.ThemeMode
import com.futo.platformplayer.compose.ui.audioLanguageDisplayName
import com.futo.platformplayer.compose.ui.supportedAudioLanguageCodes

@Composable
fun SettingsScreen(
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onManageSources: () -> Unit,
    onImportDatabase: () -> Unit,
    onImportNewPipeDatabase: () -> Unit = {},
    activeSourceCount: Int,
    defaultPlaybackSpeed: Float,
    onDefaultPlaybackSpeedChange: (Float) -> Unit,
    perChannelPlaybackSpeedEnabled: Boolean,
    onPerChannelPlaybackSpeedChange: (Boolean) -> Unit,
    preferredVideoQuality: Int,
    onPreferredVideoQualityChange: (Int) -> Unit,
    preferredAudioBitrate: Int,
    onPreferredAudioBitrateChange: (Int) -> Unit,
    preferredAudioLanguage: String,
    onPreferredAudioLanguageChange: (String) -> Unit,
    preferOriginalAudio: Boolean,
    onPreferOriginalAudioChange: (Boolean) -> Unit,
    stickyCaptionsEnabled: Boolean,
    onStickyCaptionsChange: (Boolean) -> Unit,
    showRecommendations: Boolean,
    onShowRecommendationsChange: (Boolean) -> Unit,
    searchHistoryEnabled: Boolean,
    onSearchHistoryChange: (Boolean) -> Unit,
    keepScreenAwake: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    pictureInPictureEnabled: Boolean,
    onPictureInPictureChange: (Boolean) -> Unit,
    otherAudioDuckingEnabled: Boolean,
    onOtherAudioDuckingChange: (Boolean) -> Unit,
    otherAudioDuckVolumePercent: Int,
    onOtherAudioDuckVolumeChange: (Int) -> Unit,
    pcLink: PcLinkUiState = PcLinkUiState(),
    onScanPcPairingQr: () -> Unit = {},
    onRemovePairedComputer: (String) -> Unit = {},
    availableUpdate: ReleaseUpdateUiModel? = null,
) {
    val uriHandler = LocalUriHandler.current
    var showSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showDuckVolumeDialog by rememberSaveable { mutableStateOf(false) }
    var showPairedComputers by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.testTag("settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_tagline), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.settings_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        availableUpdate?.let { update ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update-available-banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.NewReleases, contentDescription = null)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.update_available),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(
                                        R.string.update_available_description,
                                        update.versionName,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        FilledTonalButton(
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("download-update"),
                            onClick = { uriHandler.openUri(update.releaseUrl) },
                        ) {
                            Text(stringResource(R.string.open_release_page))
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                LinkSetting(
                    title = stringResource(R.string.settings_appearance),
                    description = stringResource(
                        when (themeMode) {
                            ThemeMode.System -> R.string.automatic
                            ThemeMode.Light -> R.string.use_light_theme
                            ThemeMode.Dark -> R.string.use_dark_theme
                        },
                    ),
                    icon = Icons.Outlined.DarkMode,
                    onClick = { showThemeDialog = true },
                    testTag = "theme-mode",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.use_wallpaper_colors),
                    description = stringResource(R.string.material_you_dynamic_color),
                    icon = Icons.Outlined.DarkMode,
                    checked = dynamicColorsEnabled,
                    onCheckedChange = onDynamicColorsChange,
                    testTag = "toggle-dynamic-colors",
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_playback)) {
                LinkSetting(
                    title = stringResource(R.string.default_playback_speed),
                    description = "${defaultPlaybackSpeed}x",
                    icon = Icons.Outlined.Speed,
                    onClick = { showSpeedDialog = true },
                    testTag = "default-playback-speed",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.per_channel_playback_speed),
                    description = stringResource(R.string.per_channel_playback_speed_description),
                    icon = Icons.Outlined.Speed,
                    checked = perChannelPlaybackSpeedEnabled,
                    onCheckedChange = onPerChannelPlaybackSpeedChange,
                    testTag = "per-channel-playback-speed",
                )
                HorizontalDivider()
                LinkSetting(
                    title = stringResource(R.string.preferred_quality),
                    description = if (preferredVideoQuality == 0) {
                        stringResource(R.string.automatic)
                    } else {
                        stringResource(R.string.quality_maximum, preferredVideoQuality)
                    },
                    icon = Icons.Outlined.HighQuality,
                    onClick = { showQualityDialog = true },
                    testTag = "preferred-video-quality",
                )
                HorizontalDivider()
                LinkSetting(
                    title = stringResource(R.string.preferred_audio_quality),
                    description = stringResource(
                        if (preferredAudioBitrate == Int.MAX_VALUE) {
                            R.string.high_quality
                        } else {
                            R.string.low_data
                        },
                    ),
                    icon = Icons.Outlined.MusicNote,
                    onClick = { showAudioQualityDialog = true },
                    testTag = "preferred-audio-quality",
                )
                HorizontalDivider()
                LinkSetting(
                    title = stringResource(R.string.primary_audio_language),
                    description = audioLanguageDisplayName(preferredAudioLanguage),
                    icon = Icons.Outlined.Language,
                    onClick = { showAudioLanguageDialog = true },
                    testTag = "preferred-audio-language",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.prefer_original_audio),
                    description = stringResource(R.string.prefer_original_audio_description),
                    icon = Icons.Outlined.Language,
                    checked = preferOriginalAudio,
                    onCheckedChange = onPreferOriginalAudioChange,
                    testTag = "prefer-original-audio",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.remember_subtitles),
                    description = stringResource(R.string.remember_subtitles_description),
                    icon = Icons.Outlined.ClosedCaption,
                    checked = stickyCaptionsEnabled,
                    onCheckedChange = onStickyCaptionsChange,
                    testTag = "sticky-captions",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.keep_screen_awake),
                    description = stringResource(R.string.keep_screen_awake_description),
                    icon = Icons.Outlined.ScreenLockPortrait,
                    checked = keepScreenAwake,
                    onCheckedChange = onKeepScreenAwakeChange,
                    testTag = "keep-screen-awake",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.picture_in_picture),
                    description = stringResource(R.string.picture_in_picture_description),
                    icon = Icons.Outlined.PictureInPicture,
                    checked = pictureInPictureEnabled,
                    onCheckedChange = onPictureInPictureChange,
                    testTag = "picture-in-picture",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.lower_volume_for_other_audio),
                    description = stringResource(R.string.lower_volume_for_other_audio_description),
                    icon = Icons.AutoMirrored.Outlined.VolumeDown,
                    checked = otherAudioDuckingEnabled,
                    onCheckedChange = onOtherAudioDuckingChange,
                    testTag = "other-audio-ducking",
                )
                HorizontalDivider()
                LinkSetting(
                    title = stringResource(R.string.reduced_playback_volume),
                    description = stringResource(
                        R.string.reduced_playback_volume_value,
                        otherAudioDuckVolumePercent,
                    ),
                    icon = Icons.AutoMirrored.Outlined.VolumeDown,
                    onClick = { showDuckVolumeDialog = true },
                    testTag = "other-audio-duck-volume",
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_content)) {
                ToggleSetting(
                    title = stringResource(R.string.show_recommendations),
                    description = stringResource(R.string.show_recommendations_description),
                    icon = Icons.Outlined.Recommend,
                    checked = showRecommendations,
                    onCheckedChange = onShowRecommendationsChange,
                    testTag = "show-recommendations",
                )
                HorizontalDivider()
                ToggleSetting(
                    title = stringResource(R.string.search_history),
                    description = stringResource(R.string.search_history_description),
                    icon = Icons.Outlined.Search,
                    checked = searchHistoryEnabled,
                    onCheckedChange = onSearchHistoryChange,
                    testTag = "search-history",
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_privacy)) {
                ToggleSetting(
                    title = stringResource(R.string.private_session),
                    description = stringResource(R.string.private_session_description),
                    icon = Icons.Outlined.CloudOff,
                    checked = privateSessionEnabled,
                    onCheckedChange = onPrivateSessionChange,
                    testTag = "toggle-private-session",
                )
                HorizontalDivider()
                LinkSetting(
                    title = stringResource(R.string.privacy_controls),
                    description = stringResource(R.string.privacy_controls_description),
                    icon = Icons.Outlined.PrivacyTip,
                    onClick = {},
                    testTag = "privacy-controls",
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_sources)) {
                LinkSetting(
                    title = stringResource(R.string.manage_sources),
                    description = pluralStringResource(
                        R.plurals.active_on_device,
                        activeSourceCount,
                        activeSourceCount,
                    ),
                    icon = Icons.Outlined.Extension,
                    onClick = onManageSources,
                    testTag = "manage-sources",
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_connections)) {
                LinkSetting(
                    title = stringResource(R.string.paired_computers),
                    description = stringResource(
                        R.string.paired_computers_count,
                        pcLink.pairedComputers.size,
                    ),
                    icon = Icons.Outlined.Computer,
                    onClick = { showPairedComputers = true },
                    testTag = "paired-computers",
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_data)) {
                LinkSetting(
                    title = stringResource(R.string.import_grayjay_database),
                    description = stringResource(R.string.import_grayjay_database_description),
                    icon = Icons.Outlined.FileUpload,
                    onClick = onImportDatabase,
                    testTag = "import-grayjay-database",
                )
                HorizontalDivider()
                LinkSetting(
                    title = stringResource(R.string.import_newpipe_database),
                    description = stringResource(R.string.import_newpipe_database_description),
                    icon = Icons.Outlined.FileUpload,
                    onClick = onImportNewPipeDatabase,
                    testTag = "import-newpipe-database",
                )
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag("version-information"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(
                        R.string.version_information,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_appearance),
            choices = listOf(
                ThemeMode.System to stringResource(R.string.automatic),
                ThemeMode.Light to stringResource(R.string.use_light_theme),
                ThemeMode.Dark to stringResource(R.string.use_dark_theme),
            ),
            selected = themeMode,
            onDismiss = { showThemeDialog = false },
            onChoose = {
                onThemeModeChange(it)
                showThemeDialog = false
            },
        )
    }

    if (showSpeedDialog) {
        ChoiceDialog(
            title = stringResource(R.string.default_playback_speed),
            choices = listOf(0.5f to "0.5x", 0.75f to "0.75x", 1f to "1x", 1.25f to "1.25x", 1.5f to "1.5x", 2f to "2x"),
            selected = defaultPlaybackSpeed,
            onDismiss = { showSpeedDialog = false },
            onChoose = {
                onDefaultPlaybackSpeedChange(it)
                showSpeedDialog = false
            },
        )
    }
    if (showQualityDialog) {
        ChoiceDialog(
            title = stringResource(R.string.preferred_video_quality),
            choices = listOf(0 to stringResource(R.string.automatic), 2160 to "2160p", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p"),
            selected = preferredVideoQuality,
            onDismiss = { showQualityDialog = false },
            onChoose = {
                onPreferredVideoQualityChange(it)
                showQualityDialog = false
            },
        )
    }
    if (showAudioQualityDialog) {
        ChoiceDialog(
            title = stringResource(R.string.preferred_audio_quality),
            choices = listOf(
                Int.MAX_VALUE to stringResource(R.string.high_quality),
                1 to stringResource(R.string.low_data),
            ),
            selected = preferredAudioBitrate,
            onDismiss = { showAudioQualityDialog = false },
            onChoose = {
                onPreferredAudioBitrateChange(it)
                showAudioQualityDialog = false
            },
        )
    }
    if (showAudioLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.primary_audio_language),
            choices = supportedAudioLanguageCodes.map { language ->
                language to audioLanguageDisplayName(language)
            },
            selected = preferredAudioLanguage,
            onDismiss = { showAudioLanguageDialog = false },
            onChoose = {
                onPreferredAudioLanguageChange(it)
                showAudioLanguageDialog = false
            },
        )
    }
    if (showDuckVolumeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.reduced_playback_volume),
            choices = listOf(20, 25, 35, 45, 55, 65).map { percent ->
                percent to stringResource(R.string.reduced_playback_volume_value, percent)
            },
            selected = otherAudioDuckVolumePercent,
            onDismiss = { showDuckVolumeDialog = false },
            onChoose = {
                onOtherAudioDuckVolumeChange(it)
                showDuckVolumeDialog = false
            },
        )
    }
    if (showPairedComputers) {
        PairedComputersDialog(
            pcLink = pcLink,
            onScanQr = onScanPcPairingQr,
            onRemove = onRemovePairedComputer,
            onDismiss = { showPairedComputers = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onChoose: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(choices.size) { index ->
                    val (value, label) = choices[index]
                    ListItem(
                        modifier = Modifier.clickable { onChoose(value) },
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = value == selected, onClick = null)
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        },
    )
}

@Composable
private fun LinkSetting(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
) {
    ListItem(
        modifier = Modifier
            .testTag(testTag)
            .clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.open_item, title),
            )
        },
    )
}
