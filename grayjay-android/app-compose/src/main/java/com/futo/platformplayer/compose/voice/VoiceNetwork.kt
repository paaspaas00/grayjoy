package com.futo.platformplayer.compose.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal fun voiceInternetAvailable(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)

internal class VoiceOfflineException : java.io.IOException("No validated Internet connection")
