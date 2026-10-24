package com.futo.platformplayer.compose.diagnostics

import android.content.Context

internal object AdvancedOptions {
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences("grayjoy_advanced_options", Context.MODE_PRIVATE)
    fun isUnlocked(context: Context) = runCatching { preferences(context).getBoolean("unlocked", false) }.getOrDefault(false)
    fun unlock(context: Context) { preferences(context).edit().putBoolean("unlocked", true).apply() }
    fun showBackendChanges(context: Context) = runCatching { preferences(context).getBoolean("backend_change_notices", false) }.getOrDefault(false)
    fun setShowBackendChanges(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("backend_change_notices", enabled).apply()
    }
}

internal class AdvancedUnlockTaps {
    private var lastTap = 0L
    private var taps = 0
    fun tap(now: Long): Int {
        if (now - lastTap > 5_000L || now < lastTap) taps = 0
        lastTap = now
        taps = (taps + 1).coerceAtMost(10)
        return 10 - taps
    }
}
