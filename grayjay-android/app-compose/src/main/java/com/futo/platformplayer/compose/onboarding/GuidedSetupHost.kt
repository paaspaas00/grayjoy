package com.futo.platformplayer.compose.onboarding

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.futo.platformplayer.compose.ui.*

internal data class GuidedSetupControls(
    val open: () -> Unit = {},
    val sponsorAutomatic: Boolean = true,
    val setSponsorAutomatic: (Boolean) -> Unit = {},
)
internal val LocalGuidedSetup = compositionLocalOf { GuidedSetupControls() }

@Composable
internal fun GuidedSetupHost(
    state: GrayjayUiState,
    actions: GrayjayAppActions,
    allowStartupDialogs: Boolean,
    launcherRequest: Int = 0,
    externalRequest: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { GuidedSetupPreferences(context) }
    var version6UpgradeGuide by remember {
        mutableStateOf(preferences.requiresVersion6UpgradeGuide())
    }
    val pendingCrash by com.futo.platformplayer.compose.diagnostics.CrashLogStore.pendingCrash.collectAsState()
    var visible by rememberSaveable {
        mutableStateOf(allowStartupDialogs && (preferences.showOnStartup || version6UpgradeGuide))
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var session by rememberSaveable { mutableIntStateOf(0) }
    var handledLauncher by rememberSaveable { mutableIntStateOf(launcherRequest) }
    var handledExternal by rememberSaveable { mutableIntStateOf(externalRequest) }
    fun startGuide() {
        page = 0
        session++
        visible = true
    }
    LaunchedEffect(allowStartupDialogs, version6UpgradeGuide) {
        // Saved Activity state from the previous version may restore visible=false. A pending
        // migration wins until the user explicitly completes or closes the guide.
        if (allowStartupDialogs && version6UpgradeGuide) startGuide()
    }
    LaunchedEffect(launcherRequest) {
        if (launcherRequest != handledLauncher) {
            handledLauncher = launcherRequest
            version6UpgradeGuide = preferences.requiresVersion6UpgradeGuide()
            if (preferences.showOnStartup || version6UpgradeGuide) startGuide()
            else visible = false
        }
    }
    LaunchedEffect(externalRequest) {
        if (externalRequest != handledExternal) {
            handledExternal = externalRequest
            visible = false
        }
    }
    CompositionLocalProvider(LocalGuidedSetup provides GuidedSetupControls(
        open = ::startGuide,
        sponsorAutomatic = state.sponsorBlockAutomaticSkip,
        setSponsorAutomatic = actions.onSponsorBlockAutomaticSkipChange,
    )) {
        content()
        if (visible && pendingCrash == null) key(session) {
            GuidedSetupDialog(
                state, actions, page, { page = it },
                onFinish = { neverAgain ->
                    if (version6UpgradeGuide) {
                        preferences.markVersion6UpgradeGuidePresented()
                        version6UpgradeGuide = false
                    }
                    preferences.showOnStartup = !neverAgain
                    visible = false
                },
                onExternalStep = { action -> visible = false; action() },
            )
        }
    }
}
