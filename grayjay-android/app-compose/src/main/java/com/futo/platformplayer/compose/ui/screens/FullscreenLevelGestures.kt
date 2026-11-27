package com.futo.platformplayer.compose.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.ui.LocalInteractionControls
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun gestureLevel(initial: Float, dy: Float, height: Float): Float =
    ((initial.takeIf(Float::isFinite) ?: .5f) +
        if (height.isFinite() && height > 0f && dy.isFinite()) -dy / (height * .65f) else 0f).coerceIn(0f, 1f)

@Stable internal class PlayerLevelGesture {
    var volume by mutableStateOf(true)
    var level by mutableFloatStateOf(0f)
    var showing by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var dragging by mutableStateOf(false)
}

@Composable
internal fun rememberPlayerLevelGesture(fullscreen: Boolean): PlayerLevelGesture {
    val state = remember { PlayerLevelGesture() }
    val window = LocalContext.current.gestureActivity()?.window
    val controls = LocalInteractionControls.current
    DisposableEffect(fullscreen, controls.brightnessGesture, window) {
        val original = window?.attributes?.screenBrightness
        onDispose {
            if (fullscreen && controls.brightnessGesture && original != null) window?.let {
                it.attributes = it.attributes.apply { screenBrightness = original }
            }
            state.showing = false
        }
    }
    LaunchedEffect(state.revision, state.dragging) {
        if (!state.dragging) { delay(800); state.showing = false }
    }
    return state
}

@Composable
internal fun Modifier.fullscreenLevelGestures(fullscreen: Boolean, locked: Boolean, state: PlayerLevelGesture): Modifier {
    val controls = LocalInteractionControls.current
    val context = LocalContext.current
    val activity = context.gestureActivity()
    val audio = context.getSystemService(AudioManager::class.java)
    if (!fullscreen || locked || (!controls.volumeGesture && !controls.brightnessGesture)) return this
    return pointerInput(fullscreen, locked, controls.volumeGesture, controls.brightnessGesture) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val volume = down.position.x >= size.width / 2f
            val edge = 24.dp.toPx()
            // Leave system-edge gestures, top buttons and the seek bar to their own handlers.
            if (down.position.x < edge || down.position.x > size.width - edge ||
                down.position.y < edge || down.position.y > size.height * .78f ||
                (volume && !controls.volumeGesture) || (!volume && !controls.brightnessGesture)) return@awaitEachGesture
            val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val initial = if (volume) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
                else activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                    ?: runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(.5f)
            var claimed = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                    val delta = change.position - down.position
                    if (!claimed && abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) >= abs(delta.y)) break
                    if (!claimed && abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x) * 1.25f) claimed = true
                    if (claimed) {
                        change.consume()
                        state.dragging = true; state.volume = volume
                        state.level = gestureLevel(initial, delta.y, size.height.toFloat())
                        state.showing = true
                        if (volume) runCatching {
                            val target = (state.level * maximum).roundToInt()
                            if (target != audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        } else activity?.window?.let { window ->
                            window.attributes = window.attributes.apply { screenBrightness = state.level.coerceAtLeast(.01f) }
                        }
                    }
                }
            } finally { state.dragging = false; state.revision++ }
        }
    }
}

@Composable internal fun PlayerLevelIndicator(state: PlayerLevelGesture, modifier: Modifier) {
    AnimatedVisibility(state.showing, modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = .94f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.width(132.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Icon(if (state.volume) Icons.AutoMirrored.Outlined.VolumeUp else Icons.Outlined.Brightness6, null)
                Text("${(state.level * 100).roundToInt()}%", Modifier.testTag("player-level-percent"))
                LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
private tailrec fun Context.gestureActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.gestureActivity() else null
    else -> null
}
