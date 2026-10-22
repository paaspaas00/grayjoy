package com.futo.platformplayer.compose.onboarding

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.*
import com.futo.platformplayer.compose.ui.screens.supportsShortsFeedPlayer
import kotlinx.coroutines.launch

@Composable
internal fun GuidedSetupDialog(
    state: GrayjayUiState, actions: GrayjayAppActions,
    initialPage: Int, onPage: (Int) -> Unit,
    onFinish: (Boolean) -> Unit,
    onExternalStep: (() -> Unit) -> Unit,
) {
    var importYoutube by rememberSaveable { mutableStateOf(false) }
    var importFiles by rememberSaveable { mutableStateOf(false) }
    val steps = remember(importYoutube, importFiles) { guidedSetupSteps(importYoutube, importFiles) }
    val currentSteps by rememberUpdatedState(steps)
    val pager = rememberPagerState(initialPage.coerceIn(0, steps.lastIndex)) { currentSteps.size }
    val scope = rememberCoroutineScope()
    var neverAgain by rememberSaveable { mutableStateOf(true) }
    var showNoImportInfo by rememberSaveable { mutableStateOf(false) }
    var importSubscriptions by rememberSaveable { mutableStateOf(true) }
    var importHistory by rememberSaveable { mutableStateOf(true) }
    var importPlaylists by rememberSaveable { mutableStateOf(true) }
    var importLiked by rememberSaveable { mutableStateOf(true) }
    val youtube = state.sources.firstOrNull { it.id == "youtube" }
    val config = LocalConfiguration.current
    val phone = supportsShortsFeedPlayer(config.screenWidthDp, config.screenHeightDp, config.smallestScreenWidthDp)
    fun next() {
        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(steps.lastIndex)) }
    }
    LaunchedEffect(pager.settledPage) { onPage(pager.settledPage) }
    Dialog(onDismissRequest = { onFinish(neverAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().testTag("guided-setup")) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    WizardMark(Modifier.size(32.dp))
                    Text(stringResource(R.string.setup_title), Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onFinish(neverAgain) }) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                }
                LinearProgressIndicator(progress = { (pager.currentPage + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                Text(stringResource(R.string.setup_step, pager.currentPage + 1, steps.size),
                    Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth(),
                    // Page count and key lookup must observe the same snapshot when an
                    // optional step is inserted/removed, including in-flight prefetches.
                    key = { currentSteps.getOrNull(it)?.name ?: "pending-step-$it" }) { page ->
                    val step = currentSteps.getOrNull(page) ?: return@HorizontalPager
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("setup-page-${step.tag}"),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(stringResource(step.title), style = MaterialTheme.typography.headlineMedium)
                        when (step) {
                            SetupStep.Welcome -> {
                                SetupIllustration(SetupDemo.Welcome, page == pager.currentPage)
                                Text(stringResource(R.string.setup_welcome_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SetupStep.Tour -> SetupTour()
                            SetupStep.Theme -> {
                                Text(stringResource(R.string.setup_theme_body), style = MaterialTheme.typography.bodyLarge)
                                listOf(ThemeMode.System to R.string.automatic, ThemeMode.Light to R.string.use_light_theme, ThemeMode.Dark to R.string.use_dark_theme).forEach { (mode, label) ->
                                    SetupChoice(stringResource(label), state.themeMode == mode) { actions.onThemeModeChange(mode) }
                                }
                                SetupToggle(stringResource(R.string.use_wallpaper_colors), state.dynamicColorsEnabled, actions.onDynamicColorsChange)
                            }
                            SetupStep.YoutubeLogin -> {
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
                                    OutlinedButton(onClick = { youtube?.let(actions.onLoginSource) }, enabled = youtube != null && !state.isSwitchingProfile,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_sign_in_youtube)) }
                                }
                                if (state.youtubeImport.isRunning) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text(stringResource(R.string.setup_import_running, state.youtubeImport.completed))
                                }
                                state.youtubeImport.resultMessage?.let { Text(it) }
                                state.youtubeImport.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                }
                                if (importFiles) {
                                OutlinedButton(onClick = { onExternalStep(actions.onChooseDatabaseImport) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.import_grayjay_database))
                                }
                                OutlinedButton(onClick = { onExternalStep(actions.onChooseNewPipeImport) }, modifier = Modifier.fillMaxWidth()) {
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
                            SetupStep.SponsorBlock -> {
                                SetupIllustration(SetupDemo.Sponsor, page == pager.currentPage)
                                Text(stringResource(R.string.setup_sponsor_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.sponsorblock), state.sponsorBlockEnabled, actions.onSponsorBlockEnabledChange)
                                SetupToggle(stringResource(R.string.sponsorblock_automatic_skip), state.sponsorBlockAutomaticSkip,
                                    actions.onSponsorBlockAutomaticSkipChange, state.sponsorBlockEnabled)
                                Text(stringResource(R.string.sponsorblock_manual_description))
                            }
                            SetupStep.Autoplay -> {
                                Text(stringResource(R.string.setup_autoplay_body), style = MaterialTheme.typography.bodyLarge)
                                SetupToggle(stringResource(R.string.youtube_autoplay), state.youtubeAutoplayEnabled, actions.onYoutubeAutoplayChange)
                            }
                            SetupStep.Done -> {
                                SetupIllustration(SetupDemo.Welcome, page == pager.currentPage)
                                Text(stringResource(R.string.setup_done_body), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_later))
                            }
                        }
                    }
                }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SetupToggle(stringResource(R.string.setup_never_again), neverAgain, { neverAgain = it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0, modifier = Modifier.weight(1f).testTag("setup-back")) { Text(stringResource(R.string.back)) }
                        Button(onClick = {
                            if (pager.currentPage == steps.size - 1) onFinish(neverAgain)
                            else next()
                        }, enabled = !state.isSwitchingProfile, modifier = Modifier.weight(1f).testTag("setup-next")) {
                            Text(stringResource(if (pager.currentPage == steps.size - 1) R.string.setup_finish else R.string.setup_next))
                        }
                    }
                }
            }
        }
        }
    }
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
        Switch(checked, onCheckedChange = null, enabled = enabled)
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
private fun SetupTour() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.nav_home, R.string.nav_following, R.string.nav_search, R.string.nav_library, R.string.nav_settings)
    val bodies = listOf(R.string.setup_tour_home, R.string.setup_tour_following, R.string.setup_tour_search, R.string.setup_tour_library, R.string.setup_tour_prefs)
    Text(stringResource(R.string.setup_tour_body), style = MaterialTheme.typography.bodyLarge)
    tabs.forEachIndexed { index, label ->
        SetupChoice(stringResource(label), index == selected) { selected = index }
        if (selected == index) Text(stringResource(bodies[index]), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
