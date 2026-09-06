package com.futo.platformplayer.compose

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class DevicePerformanceProfile(
    val isLowEnd: Boolean,
    val compactContent: Boolean,
    val allowPerItemLayerAnimations: Boolean,
)

@Composable
fun rememberDevicePerformanceProfile(): DevicePerformanceProfile {
    val supplied = LocalDevicePerformanceProfile.current
    if (supplied != null) return supplied
    val context = LocalContext.current.applicationContext
    return remember(context) { devicePerformanceProfile(context) }
}

val LocalDevicePerformanceProfile = staticCompositionLocalOf<DevicePerformanceProfile?> { null }

@Volatile
private var cachedDevicePerformanceProfile: DevicePerformanceProfile? = null

internal fun devicePerformanceProfile(context: Context): DevicePerformanceProfile =
    cachedDevicePerformanceProfile ?: synchronized(DevicePerformanceProfile::class.java) {
        cachedDevicePerformanceProfile
            ?: detectDevicePerformanceProfile(context.applicationContext).also {
                cachedDevicePerformanceProfile = it
            }
    }

internal fun detectDevicePerformanceProfile(context: Context): DevicePerformanceProfile {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryClass = activityManager?.memoryClass ?: 256
    val is32BitOnly = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
    val lowEnd = activityManager?.isLowRamDevice == true ||
        memoryClass <= 192 ||
        (is32BitOnly && memoryClass <= 256)
    return DevicePerformanceProfile(
        isLowEnd = lowEnd,
        compactContent = lowEnd,
        allowPerItemLayerAnimations = !lowEnd,
    )
}
