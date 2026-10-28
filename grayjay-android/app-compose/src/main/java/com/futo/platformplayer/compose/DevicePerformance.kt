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
    val memory = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memory)
    val lowEnd = isConstrainedDevice(activityManager?.isLowRamDevice == true, memoryClass,
        is32BitOnly, memory.totalMem, Build.VERSION.SDK_INT)
    return DevicePerformanceProfile(
        isLowEnd = lowEnd,
        compactContent = lowEnd,
        allowPerItemLayerAnimations = !lowEnd,
    )
}

/** Heap limits alone misclassify older 64-bit phones with small RAM as high-end devices. */
internal fun isConstrainedDevice(lowRam: Boolean, heapMb: Int, only32Bit: Boolean, ramBytes: Long, sdk: Int): Boolean =
    lowRam || heapMb <= 192 || (only32Bit && heapMb <= 256) ||
        (ramBytes in 1..(3L * 1024 * 1024 * 1024)) ||
        (sdk <= 29 && ramBytes in 1..(4L * 1024 * 1024 * 1024))

internal fun useSmallPhoneTypography(lowEnd: Boolean, widthDp: Int, fontScale: Float): Boolean =
    lowEnd && widthDp < 390 && fontScale < 1.25f
