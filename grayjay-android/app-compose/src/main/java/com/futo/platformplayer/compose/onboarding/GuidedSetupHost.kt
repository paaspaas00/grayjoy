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
    var visible by rememberSaveable { mutableStateOf(allowStartupDialogs && preferences.showOnStartup) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var session by rememberSaveable { mutableIntStateOf(0) }
    var handledLauncher by rememberSaveable { mutableIntStateOf(launcherRequest) }
    var handledExternal by rememberSaveable { mutableIntStateOf(externalRequest) }
    fun startGuide() {
        page = 0
        session++
        visible = true
    }
    LaunchedEffect(launcherRequest) {
        if (launcherRequest != handledLauncher) {
            handledLauncher = launcherRequest
            if (preferences.showOnStartup) startGuide() else visible = false
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
        if (visible) key(session) {
            GuidedSetupDialog(
                state, actions, page, { page = it },
                onFinish = { neverAgain ->
                    preferences.showOnStartup = !neverAgain
                    visible = false
                },
                onExternalStep = { action -> visible = false; action() },
            )
        }
    }
}
