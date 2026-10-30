package com.futo.platformplayer.compose.onboarding

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import com.futo.platformplayer.compose.ui.screens.AdaptiveButton as Button
import com.futo.platformplayer.compose.ui.screens.AdaptiveOutlinedButton as OutlinedButton
import com.futo.platformplayer.compose.ui.screens.AdaptiveFilledTonalButton as FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.*
import com.futo.platformplayer.compose.ui.screens.supportsShortsFeedPlayer
import com.futo.platformplayer.compose.ui.screens.YoutubeImportProgress
import kotlinx.coroutines.launch

@Composable
internal fun GuidedSetupDialog(
    state: GrayjayUiState, actions: GrayjayAppActions,
    initialPage: Int, onPage: (Int) -> Unit,
    onFinish: (Boolean) -> Unit,
    onExternalStep: (() -> Unit) -> Unit,
) {
    CompositionLocalProvider(com.futo.platformplayer.compose.ui.screens.LocalMusicPresentation provides
        (com.futo.platformplayer.compose.ui.screens.LocalMusicPresentation.current || state.usesMusicPresentation())) {
        GuidedSetupDialogContent(state, actions, initialPage, onPage, onFinish, onExternalStep)
    }
}

@Composable
private fun GuidedSetupDialogContent(state: GrayjayUiState, actions: GrayjayAppActions,
    initialPage: Int, onPage: (Int) -> Unit, onFinish: (Boolean) -> Unit, onExternalStep: (() -> Unit) -> Unit) {
    var importYoutube by rememberSaveable { mutableStateOf(state.youtubeImport.isRunning) }
    var importFiles by rememberSaveable { mutableStateOf(false) }
    var splitProfiles by rememberSaveable { mutableStateOf(state.sourceProfilesEnabled) }
    var extraProfiles by rememberSaveable { mutableStateOf(state.profiles.map { it.sourceId }
        .filter { it.isNotBlank() && it !in setOf("youtube", "spotify") }) }
    val steps = remember(importYoutube, importFiles, splitProfiles) { guidedSetupSteps(importYoutube, importFiles, splitProfiles) }
    val currentSteps by rememberUpdatedState(steps)
    val pager = rememberPagerState(initialPage.coerceIn(0, steps.lastIndex)) { currentSteps.size }
    val scope = rememberCoroutineScope()
    val importing = state.youtubeImport.isRunning
    val busy = importing || state.sourceProfileSetup.isRunning
    val requestedProfileSources = extraProfiles.toSet() + setOf("youtube", "spotify")
    val profilesReady = state.sourceProfilesEnabled && state.profiles.map { it.sourceId }.containsAll(requestedProfileSources)
    var confirmCancelImport by rememberSaveable { mutableStateOf(false) }
    var tourSelection by rememberSaveable { mutableIntStateOf(0) }
    var showNoImportInfo by rememberSaveable { mutableStateOf(false) }
    var importSubscriptions by rememberSaveable { mutableStateOf(true) }
    var importHistory by rememberSaveable { mutableStateOf(true) }
    var importPlaylists by rememberSaveable { mutableStateOf(true) }
    var importLiked by rememberSaveable { mutableStateOf(true) }
    val youtube = state.sources.firstOrNull { it.id == "youtube" }
    val config = LocalConfiguration.current
    val phone = supportsShortsFeedPlayer(config.screenWidthDp, config.screenHeightDp, config.smallestScreenWidthDp)
    fun next() {
        if (busy) return
        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(steps.lastIndex)) }
    }
    LaunchedEffect(pager.settledPage) { onPage(pager.settledPage) }
    LaunchedEffect(importing) {
        if (importing) {
            importYoutube = true
            val target = guidedSetupSteps(true, importFiles, splitProfiles).indexOf(SetupStep.ImportDetails)
            pager.scrollToPage(target)
        }
    }
    Dialog(onDismissRequest = { if (!busy) onFinish(true) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().testTag("guided-setup")) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    WizardMark(Modifier.size(32.dp))
                    Text(stringResource(R.string.setup_title), Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onFinish(true) }, enabled = !busy) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                }
                LinearProgressIndicator(progress = { (pager.currentPage + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                Text(stringResource(R.string.setup_step, pager.currentPage + 1, steps.size),
                    Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !busy &&
                    (steps.getOrNull(pager.currentPage) != SetupStep.ExtraSourceProfiles || profilesReady),
                    // Page count and key lookup must observe the same snapshot when an
                    // optional step is inserted/removed, including in-flight prefetches.
                    key = { currentSteps.getOrNull(it)?.name ?: "pending-step-$it" }) { page ->
                    val step = currentSteps.getOrNull(page) ?: return@HorizontalPager
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("setup-page-${step.tag}"),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(stringResource(step.title), style = MaterialTheme.typography.headlineMedium)
                        when (step) {
                            SetupStep.Welcome -> {
                                GrayjoyWelcomeLogo(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(if (state.sources.isYoutubeOnly()) R.string.setup_welcome_channels_body else R.string.setup_welcome_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SetupStep.Tour -> SetupTour(tourSelection, state.sources.isYoutubeOnly())
                            SetupStep.Theme -> {
                                Text(stringResource(R.string.setup_theme_body), style = MaterialTheme.typography.bodyLarge)
                                listOf(ThemeMode.System to R.string.automatic, ThemeMode.Light to R.string.use_light_theme, ThemeMode.Dark to R.string.use_dark_theme).forEach { (mode, label) ->
                                    SetupChoice(stringResource(label), state.themeMode == mode) { actions.onThemeModeChange(mode) }
                                }
                                SetupToggle(stringResource(R.string.use_wallpaper_colors), state.dynamicColorsEnabled, actions.onDynamicColorsChange)
                            }
                            SetupStep.YoutubeLogin -> {
                                YoutubeAccountArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.setup_youtube_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_skip_login), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (youtube?.isAuthenticated == true) Text(stringResource(R.string.setup_signed_in), color = MaterialTheme.colorScheme.primary)
                                Button(onClick = { youtube?.let(actions.onLoginSource) }, enabled = youtube != null && !state.isSwitchingProfile,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.setup_sign_in_youtube)) }
                            }
                            SetupStep.OtherLogin -> {
                                Text(stringResource(R.string.setup_other_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_skip_other), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                state.sources.filter { it.isEnabled && it.id !in setOf("youtube", "spotify") && it.pluginConfigUrl.isNotBlank() }.forEach { source ->
                                    OutlinedButton(onClick = { actions.onLoginSource(source) },
                                        modifier = Modifier.fillMaxWidth()) { Text(source.name) }
                                }
                                Text(stringResource(R.string.setup_sources_later))
                            }
                            SetupStep.ImportChoice -> {
                                Text(stringResource(R.string.setup_import_choice_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.setup_import_youtube_choice), importYoutube, { importYoutube = it })
                                SetupToggle(stringResource(R.string.setup_import_files_choice), importFiles, { importFiles = it })
                                OutlinedButton(
                                    onClick = { importYoutube = false; importFiles = false; showNoImportInfo = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("setup-no-import"),
                                ) { Text(stringResource(R.string.setup_no_import)) }
                            }
                            SetupStep.ImportDetails -> {
                                Text(stringResource(if (importYoutube) R.string.setup_import_body else R.string.setup_import_files_body), style = MaterialTheme.typography.bodyLarge)
                                if (importYoutube) Text(stringResource(R.string.setup_skip_import), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (importYoutube) {
                                Text(stringResource(R.string.youtube_sync_direction), style = MaterialTheme.typography.bodyMedium)
                                SetupToggle(stringResource(R.string.setup_import_subscriptions), importSubscriptions, { importSubscriptions = it }, !state.youtubeImport.isRunning)
                                SetupToggle(stringResource(R.string.setup_import_history), importHistory, { importHistory = it }, !state.youtubeImport.isRunning)
                                SetupToggle(stringResource(R.string.setup_import_playlists), importPlaylists, { importPlaylists = it }, !state.youtubeImport.isRunning)
                                SetupToggle(stringResource(R.string.setup_import_liked), importLiked, { importLiked = it }, !state.youtubeImport.isRunning)
                                Button(onClick = { actions.onImportYoutube("youtube", YoutubeImportSelection(importSubscriptions, importHistory, importPlaylists, importLiked)) },
                                    enabled = youtube?.isAuthenticated == true && !state.youtubeImport.isRunning && !state.isSwitchingProfile &&
                                        (importSubscriptions || importHistory || importPlaylists || importLiked),
                                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_from_youtube)) }
                                if (youtube?.isAuthenticated != true) {
                                    Text(stringResource(R.string.youtube_import_login_required))
                                    OutlinedButton(onClick = { youtube?.let(actions.onLoginSource) }, enabled = youtube != null && !state.isSwitchingProfile && !importing,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_sign_in_youtube)) }
                                }
                                if (state.youtubeImport.isRunning) {
                                    YoutubeImportProgress(state.youtubeImport)
                                    TextButton(onClick = { confirmCancelImport = true }, modifier = Modifier.testTag("setup-cancel-import")) {
                                        Text(stringResource(R.string.cancel_import_action))
                                    }
                                }
                                state.youtubeImport.resultMessage?.let { Text(it) }
                                state.youtubeImport.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                state.youtubeImport.warningMessage?.let { Text(stringResource(R.string.youtube_import_warnings, it)) }
                                }
                                if (importFiles) {
                                OutlinedButton(onClick = { onExternalStep(actions.onChooseDatabaseImport) }, enabled = !importing && !state.isSwitchingProfile, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.import_grayjay_database))
                                }
                                OutlinedButton(onClick = { onExternalStep(actions.onChooseNewPipeImport) }, enabled = !importing && !state.isSwitchingProfile, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.import_newpipe_database))
                                }
                                }
                            }
                            SetupStep.Shorts -> {
                                SetupIllustration(SetupDemo.Shorts, page == pager.currentPage)
                                Text(stringResource(R.string.setup_shorts_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.brainrot_shorts), state.brainrotShortsEnabled, actions.onBrainrotShortsChange, phone)
                                if (!phone) Text(stringResource(R.string.setup_shorts_phone))
                            }
                            SetupStep.HoldSpeed -> {
                                SetupIllustration(SetupDemo.Hold, page == pager.currentPage)
                                Text(stringResource(R.string.setup_hold_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.hold_to_speed), state.holdToSpeedEnabled, actions.onHoldToSpeedChange)
                            }
                            SetupStep.PlayerLayout -> {
                                Text(stringResource(R.string.one_handed_player_description), style = MaterialTheme.typography.bodyLarge)
                                OneHandedPlayerChoice(state.oneHandedPlayerEnabled, actions.onOneHandedPlayerChange)
                                Text(stringResource(R.string.setup_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SetupStep.AudioNormalization -> {
                                AudioNormalizationArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.setup_audio_normalization_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.audio_normalization), state.audioNormalizationEnabled, actions.onAudioNormalizationChange)
                            }
                            SetupStep.Handedness -> {
                                val controls = LocalInteractionControls.current
                                Text(stringResource(R.string.left_handed_description), style = MaterialTheme.typography.bodyLarge)
                                HandednessChoice(controls.leftHanded, controls.setLeftHanded,
                                    active = page == pager.settledPage && !pager.isScrollInProgress)
                            }
                            SetupStep.SongRecognition -> {
                                val controls = LocalInteractionControls.current
                                SongRecognitionArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.setup_song_recognition_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.recognition_privacy), style = MaterialTheme.typography.bodyMedium)
                                SetupToggle(stringResource(R.string.recognize_song), controls.recognitionEnabled, controls.setRecognitionEnabled)
                            }
                            SetupStep.LibraryShortcuts -> {
                                LibraryShortcutsArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.library_shortcuts_description), style = MaterialTheme.typography.bodyLarge)
                            }
                            SetupStep.SponsorBlock -> {
                                SetupIllustration(SetupDemo.Sponsor, page == pager.currentPage)
                                Text(stringResource(R.string.setup_sponsor_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.sponsorblock), state.sponsorBlockEnabled, actions.onSponsorBlockEnabledChange)
                                SetupToggle(stringResource(R.string.sponsorblock_automatic_skip), state.sponsorBlockAutomaticSkip,
                                    actions.onSponsorBlockAutomaticSkipChange, state.sponsorBlockEnabled)
                                Text(stringResource(R.string.sponsorblock_manual_description))
                            }
                            SetupStep.Autoplay -> {
                                YoutubeAutoplayArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.setup_autoplay_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.youtube_autoplay), state.youtubeAutoplayEnabled, actions.onYoutubeAutoplayChange)
                            }
                            SetupStep.SourceProfiles -> {
                                SourceProfilesArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.profile_split_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.profile_split_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (state.sourceProfilesEnabled) Text(stringResource(R.string.profile_split_enabled))
                                else {
                                    SetupChoice(stringResource(R.string.profile_split_yes), splitProfiles) { splitProfiles = true }
                                    SetupChoice(stringResource(R.string.profile_split_no), !splitProfiles) { splitProfiles = false }
                                }
                            }
                            SetupStep.ExtraSourceProfiles -> {
                                Text(stringResource(R.string.profile_split_choose_body), style = MaterialTheme.typography.bodyLarge)
                                val sourceCatalog = state.sourceProfileCatalog.ifEmpty { state.sources }
                                val existing = state.profiles.map { it.sourceId }.toSet()
                                sourceCatalog.filter { it.id !in setOf("youtube", "spotify") }.sortedWith(
                                    compareBy<SourceUiModel> {
                                        listOf("crunchyroll", "twitch", "nebula").indexOf(it.id).takeIf { n -> n >= 0 } ?: 3
                                    }.thenBy { it.name.lowercase() }).forEach { source ->
                                    SetupToggle(source.name, source.id in extraProfiles, { checked ->
                                        extraProfiles = if (checked) extraProfiles + source.id else extraProfiles - source.id
                                    }, enabled = !busy && source.id !in existing && source.availability != SourceAvailability.MissingPlugin)
                                }
                                Text(stringResource(R.string.profile_split_offline), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (profilesReady) Text(stringResource(R.string.profile_split_enabled), color = MaterialTheme.colorScheme.primary)
                            }
                            SetupStep.Diagnostics -> {
                                CrashLogBugArtwork(active = page == pager.settledPage && !pager.isScrollInProgress)
                                Text(stringResource(R.string.setup_diagnostics_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.save_crash_logs), state.crashLoggingEnabled, actions.onCrashLoggingChange)
                                SetupToggle(stringResource(R.string.crash_logs_downloads), state.crashLogsDownloadsCopy,
                                    actions.onCrashLogsDownloadsCopyChange, state.crashLoggingEnabled)
                                Text(stringResource(R.string.crash_logs_downloads_description), style = MaterialTheme.typography.bodyMedium)
                            }
                            SetupStep.Help -> {
                                Icon(Icons.Outlined.HelpOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.setup_help_body), style = MaterialTheme.typography.bodyLarge)
                            }
                            SetupStep.Done -> {
                                SetupIllustration(SetupDemo.Welcome, page == pager.currentPage)
                                Text(stringResource(R.string.setup_done_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_later))
                            }
                        }
                    }
                }
                if (steps.getOrNull(pager.currentPage) == SetupStep.Tour) {
                    SetupTourNavigation(tourSelection) { tourSelection = it }
                }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val createsProfiles = steps.getOrNull(pager.currentPage) == SetupStep.ExtraSourceProfiles && !profilesReady
                    if (state.sourceProfileSetup.isRunning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.profile_split_progress, state.sourceProfileSetup.completed, state.sourceProfileSetup.total),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (createsProfiles) state.sourceProfileSetup.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { if (!busy) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0 && !busy, modifier = Modifier.weight(1f).testTag("setup-back")) { Text(stringResource(R.string.back)) }
                        Button(onClick = {
                            if (busy) return@Button
                            if (createsProfiles) actions.onConfigureSourceProfiles(requestedProfileSources)
                            else if (pager.currentPage == steps.size - 1) onFinish(true)
                            else next()
                        }, enabled = !state.isSwitchingProfile && !busy,
                            modifier = Modifier.weight(1f).testTag(if (createsProfiles) "setup-create-source-profiles" else "setup-next")) {
                            Text(stringResource(if (createsProfiles) R.string.create else if (pager.currentPage == steps.size - 1) R.string.setup_finish else R.string.setup_next))
                        }
                    }
                }
            }
        }
        }
    }
    if (confirmCancelImport) AlertDialog(
        onDismissRequest = { confirmCancelImport = false },
        title = { Text(stringResource(R.string.cancel_youtube_import_title)) },
        text = { Text(stringResource(R.string.cancel_youtube_import_body)) },
        confirmButton = { TextButton(onClick = {
            confirmCancelImport = false
            actions.onCancelYoutubeImportJobs()
        }) { Text(stringResource(R.string.cancel_import_action)) } },
        dismissButton = { TextButton(onClick = { confirmCancelImport = false }) { Text(stringResource(R.string.continue_import_action)) } },
    )
    if (showNoImportInfo) AlertDialog(
        onDismissRequest = { showNoImportInfo = false },
        title = { Text(stringResource(R.string.setup_later_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_no_import_later))
                if (state.youtubeImport.isRunning) Text(stringResource(R.string.setup_existing_import_continues))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (showNoImportInfo) {
                    showNoImportInfo = false
                    next()
                }
            }, modifier = Modifier.testTag("setup-no-import-confirm")) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

@Composable
internal fun SetupToggle(label: String, checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = change).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        com.futo.platformplayer.compose.ui.screens.AdaptiveSwitch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SetupChoice(label: String, selected: Boolean, choose: () -> Unit) {
    OutlinedCard(onClick = choose, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = null)
            Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SetupTour(selected: Int, youtubeOnly: Boolean) {
    val tabs = listOf(R.string.nav_home, R.string.nav_following, R.string.nav_search, R.string.nav_library, R.string.nav_settings)
    val bodies = listOf(R.string.setup_tour_home, R.string.setup_tour_following, R.string.setup_tour_search, R.string.setup_tour_library, R.string.setup_tour_prefs)
    Text(stringResource(R.string.setup_tour_body), style = MaterialTheme.typography.bodyLarge)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(GrayjayDestination.entries[selected].icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(tabs[selected]), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (selected == 1 && youtubeOnly) R.string.setup_tour_following_channels else bodies[selected]), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("setup-tour-description"))
        }
    }
}

@Composable
private fun SetupTourNavigation(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0), tonalElevation = 0.dp,
        modifier = Modifier.testTag("setup-tour-navigation")) {
        GrayjayDestination.entries.filter { it.showInCompactNavigation }.forEachIndexed { index, tab ->
            NavigationBarItem(selected = index == selected, onClick = { onSelect(index) },
                icon = { Icon(tab.icon, null) },
                label = { Text(stringResource(tab.navigationLabelRes), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("setup-tour-${tab.name.lowercase()}"))
        }
    }
}
