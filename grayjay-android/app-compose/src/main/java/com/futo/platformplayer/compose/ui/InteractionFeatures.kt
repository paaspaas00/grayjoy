package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal class InteractionFeatures(context: Context) {
    val storage: SharedPreferences = context.applicationContext.getSharedPreferences("interaction_features", Context.MODE_PRIVATE)
    private val prefs = com.futo.platformplayer.compose.ResilientPreferences(storage)
    var leftHanded: Boolean
        get() = prefs.getBoolean("left_handed", false)
        set(value) { prefs.edit().putBoolean("left_handed", value).apply() }
    var recognitionEnabled: Boolean
        get() = prefs.getBoolean("song_recognition", true)
        set(value) { prefs.edit().putBoolean("song_recognition", value).apply() }
}
internal data class InteractionControls(
    val leftHanded: Boolean = false, val recognitionEnabled: Boolean = true,
    val setLeftHanded: (Boolean) -> Unit = {}, val setRecognitionEnabled: (Boolean) -> Unit = {},
    val recognizing: Boolean = false, val recognize: () -> Unit = {},
    val dismissRecognition: () -> Unit = {},
)
internal val LocalInteractionControls = compositionLocalOf { InteractionControls() }
internal val LocalBrainrotPlayer = compositionLocalOf { false }

@Composable
internal fun InteractionFeatureHost(profileId: String, resolve: suspend (String) -> VideoUiModel,
    open: (String, Boolean) -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { InteractionFeatures(context) }
    var revision by remember { mutableIntStateOf(0) }
    var recognizing by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.storage.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.storage.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val controls = remember(revision, recognizing) { InteractionControls(prefs.leftHanded, prefs.recognitionEnabled,
        { prefs.leftHanded = it }, { prefs.recognitionEnabled = it }, recognizing,
        { if (prefs.recognitionEnabled) recognizing = true else android.widget.Toast.makeText(context,
            com.futo.platformplayer.compose.R.string.recognition_disabled, android.widget.Toast.LENGTH_SHORT).show() },
        { recognizing = false }) }
    CompositionLocalProvider(LocalInteractionControls provides controls) {
        com.futo.platformplayer.compose.recognition.RecognitionTransitionHost(profileId, resolve, open,
            dismiss = { recognizing = false }, isVisible = { recognizing }) {
            PlayerCardTransitionHost(profileId, content)
        }
    }
}

@Composable
internal fun Modifier.recognitionBlur(): Modifier {
    val phase = com.futo.platformplayer.compose.recognition.LocalRecognitionTransition.current?.phase
    val radius by animateDpAsState(if (LocalInteractionControls.current.recognizing &&
        phase != com.futo.platformplayer.compose.recognition.RecognitionOpenPhase.Completed) 14.dp else 0.dp,
        tween(180), label = "recognition-background-blur")
    return blur(radius)
}
