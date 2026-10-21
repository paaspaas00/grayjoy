package com.futo.platformplayer.compose.voice

import android.content.Context
import android.content.Intent

internal object VoiceAccess {
    fun companionPackage(context: Context) = "com.futo.platformplayer.voice" +
        context.packageName.removePrefix("com.futo.platformplayer.compose")
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(companionPackage(context), 0)
        context.packageManager.checkSignatures(context.packageName, companionPackage(context)) ==
            android.content.pm.PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)
    fun enabled(context: Context) = isInstalled(context) &&
        readVoiceAccessState(context.getSharedPreferences("voice_access", 0)).enabled
    fun sessionRevision(context: Context) =
        readVoiceAccessState(context.getSharedPreferences("voice_access", 0)).revision
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences("voice_access", 0).edit().putBoolean("enabled", value && isInstalled(context)).apply()
        if (!value) stopListening(context)
    }
    fun stopListening(context: Context) {
        context.getSharedPreferences("voice_access", 0).edit().putLong("revision", sessionRevision(context) + 1).apply()
        context.sendBroadcast(Intent("${context.packageName}.VOICE_DISABLED")
            .setPackage(companionPackage(context)), "${context.packageName}.permission.VOICE_CONTROL")
    }
    fun open(context: Context) {
        if (isInstalled(context)) context.packageManager.getLaunchIntentForPackage(companionPackage(context))
            ?.let { context.startActivity(it) }
    }
}

internal data class VoiceAccessState(val enabled: Boolean, val revision: Long)

/** Unknown/corrupt authorization state cannot grant access or crash a bound service. */
internal fun readVoiceAccessState(preferences: android.content.SharedPreferences): VoiceAccessState = try {
    VoiceAccessState(preferences.getBoolean("enabled", false), preferences.getLong("revision", 0L))
} catch (_: ClassCastException) {
    VoiceAccessState(false, 0L)
}
