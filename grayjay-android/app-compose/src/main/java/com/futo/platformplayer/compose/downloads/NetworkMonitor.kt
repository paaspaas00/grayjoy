package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.io.Closeable
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Process-local connectivity gate for the plugin-resolution portion of a download.
 *
 * Media3 owns prepared transfers and applies its own [androidx.media3.exoplayer.scheduler.Requirements].
 * Resolving a Grayjay plugin happens before Media3 sees the request, so it needs an equivalent
 * gate or a normal offline error gets persisted as a permanent source failure.
 */
internal class NetworkMonitor(context: Context) : Closeable {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val _available = MutableStateFlow(queryAvailability())
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = refresh()
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        refresh()
    }

    fun isAvailable(): Boolean = queryAvailability().also { _available.value = it }

    suspend fun awaitAvailable() {
        if (isAvailable()) return
        _available.filter { it }.first()
    }

    /** Briefly backs off transient socket failures even if Android already reports recovery. */
    suspend fun awaitRecovery(attempt: Int) {
        if (!isAvailable()) {
            awaitAvailable()
        } else {
            delay((1_000L shl attempt.coerceIn(0, 4)).coerceAtMost(15_000L))
        }
    }

    private fun refresh() {
        _available.value = queryAvailability()
    }

    private fun queryAvailability(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun close() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}

/** Network failures that should pause a queued preparation rather than become source failures. */
internal fun Throwable.isRecoverableConnectivityFailure(): Boolean {
    val causes = generateSequence(this) { it.cause }.take(12).toList()
    if (causes.any {
            it is UnknownHostException ||
                it is ConnectException ||
                it is SocketTimeoutException ||
                it is SocketException ||
                (it is HttpDataSource.HttpDataSourceException &&
                    it !is HttpDataSource.InvalidResponseCodeException)
        }
    ) return true

    val combinedMessage = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
    return RECOVERABLE_NETWORK_MESSAGES.any(combinedMessage::contains)
}

private val RECOVERABLE_NETWORK_MESSAGES = listOf(
    "unable to resolve host",
    "name or service not known",
    "network is unreachable",
    "no route to host",
    "failed to connect",
    "connection reset",
    "connection abort",
    "software caused connection abort",
    "socket closed",
    "timed out",
    "timeout",
)
