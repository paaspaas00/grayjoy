package com.futo.platformplayer.compose

import android.database.ContentObserver
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class PlayerDeviceRotation(val enabled: Boolean = false, val landscape: Boolean = false)

internal fun playerLandscapeForSensorAngle(angle: Int, previous: Boolean): Boolean = when (angle) {
    in 65..115, in 245..295 -> true
    in 0..25, in 155..205, in 335..359 -> false
    else -> previous
}

/** Observe the system lock; never write it. The sensor runs only for the foreground player. */
@Composable
internal fun rememberPlayerDeviceRotation(eligible: Boolean): PlayerDeviceRotation {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var rotation by remember { mutableStateOf(PlayerDeviceRotation()) }
    DisposableEffect(context, lifecycle, eligible) {
        var observing = false
        var sensing = false
        var systemEnabled = false
        val listener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (!sensing || orientation == ORIENTATION_UNKNOWN) return
                rotation = PlayerDeviceRotation(true, playerLandscapeForSensorAngle(orientation, rotation.landscape))
            }
        }
        fun refresh() {
            systemEnabled = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            }.getOrDefault(false)
            val shouldSense = eligible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                systemEnabled && listener.canDetectOrientation()
            if (shouldSense && !sensing) {
                sensing = true
                rotation = rotation.copy(enabled = false)
                listener.enable()
            } else if (!shouldSense) {
                sensing = false
                listener.disable()
                rotation = rotation.copy(enabled = false)
            }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        fun updateLifecycle() {
            if (eligible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (!observing) {
                    context.contentResolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
                    observing = true
                }
            } else if (observing) {
                context.contentResolver.unregisterContentObserver(observer)
                observing = false
            }
            refresh()
        }
        val lifecycleObserver = LifecycleEventObserver { _, _ -> updateLifecycle() }
        lifecycle.addObserver(lifecycleObserver)
        updateLifecycle()
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            if (observing) context.contentResolver.unregisterContentObserver(observer)
            sensing = false
            listener.disable()
        }
    }
    return if (eligible) rotation else PlayerDeviceRotation()
}
