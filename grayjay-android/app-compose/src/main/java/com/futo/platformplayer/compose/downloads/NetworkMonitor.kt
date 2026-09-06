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
    private val availability = connectivityManager.activeNetwork.let { network ->
        NetworkAvailabilityTracker(network, network?.let {
            connectivityManager.getNetworkCapabilities(it)?.hasValidatedInternet()
        } == true)
    }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = availability.onAvailable(network)
        override fun onLost(network: Network) = availability.onLost(network)

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = availability.onCapabilitiesChanged(network, networkCapabilities.hasValidatedInternet())

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) =
            availability.onBlockedStatusChanged(network, blocked)
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    fun isAvailable(): Boolean = availability.available.value

    suspend fun awaitAvailable() {
        if (isAvailable()) return
        availability.available.filter { it }.first()
    }

    /** Briefly backs off transient socket failures even if Android already reports recovery. */
    suspend fun awaitRecovery(attempt: Int) {
        if (!isAvailable()) {
            awaitAvailable()
        } else {
            delay((1_000L shl attempt.coerceIn(0, 4)).coerceAtMost(15_000L))
        }
    }

    override fun close() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

/** Callback arguments are ordered; querying ConnectivityManager inside them can return stale data. */
internal class NetworkAvailabilityTracker<N>(initialNetwork: N? = null, initiallyValidated: Boolean = false) {
    private var currentNetwork = initialNetwork
    private var validated = initiallyValidated
    private var blocked = false
    val available = MutableStateFlow(initialNetwork != null && initiallyValidated)

    @Synchronized
    fun onAvailable(network: N) {
        currentNetwork = network
        validated = false
        blocked = false
        publish()
    }

    @Synchronized
    fun onCapabilitiesChanged(network: N, hasValidatedInternet: Boolean) {
        if (network != currentNetwork) return
        validated = hasValidatedInternet
        publish()
    }

    @Synchronized
    fun onBlockedStatusChanged(network: N, isBlocked: Boolean) {
        if (network != currentNetwork) return
        blocked = isBlocked
        publish()
    }

    @Synchronized
    fun onLost(network: N) {
        if (network != currentNetwork) return
        currentNetwork = null
        validated = false
        publish()
    }

    private fun publish() {
        available.value = currentNetwork != null && validated && !blocked
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
