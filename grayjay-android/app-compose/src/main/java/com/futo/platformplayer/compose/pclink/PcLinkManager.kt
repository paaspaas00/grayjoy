package com.futo.platformplayer.compose.pclink

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PcLinkManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val computers = linkedMapOf<String, PairedComputer>()
    private val playbacks = ConcurrentHashMap<String, PcPlaybackState>()
    private val commands = ConcurrentHashMap<String, MutableList<PcRemoteCommand>>()
    private val usedNonces = ConcurrentHashMap<String, Long>()
    // Keep command IDs above any sequence the browser may retain while this Android process is
    // restarted. Epoch milliseconds stay exactly representable by JavaScript numbers.
    private val commandSequence = AtomicLong(System.currentTimeMillis())
    private var serverAddresses = currentPhysicalLanServerUrls()
    private val _snapshot = MutableStateFlow(PcLinkSnapshot())
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshServerAddresses()
        override fun onLost(network: Network) = refreshServerAddresses()

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties,
        ) = refreshServerAddresses()
    }

    val snapshot = _snapshot.asStateFlow()

    init {
        loadComputers().forEach { computers[it.id] = it }
        publish()
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    // Track the physical Wi-Fi/Ethernet link even when a VPN is the default
                    // network, otherwise a DHCP address change can leave the pairing page stale.
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                networkCallback,
            )
        }
        refreshServerAddresses()
    }

    @Synchronized
    fun pair(rawPayload: String): Boolean {
        val payload = PcLinkProtocol.parsePairingPayload(rawPayload) ?: return false
        val existing = computers[payload.computerId]
        computers[payload.computerId] = PairedComputer(
            id = payload.computerId,
            name = payload.computerName,
            secret = payload.secret,
            pairedAtMs = existing?.pairedAtMs ?: System.currentTimeMillis(),
            lastSeenAtMs = existing?.lastSeenAtMs ?: 0L,
            lastKnownAddress = existing?.lastKnownAddress.orEmpty(),
        )
        saveComputers()
        publish()
        return true
    }

    @Synchronized
    fun remove(computerId: String) {
        computers.remove(computerId)
        playbacks.remove(computerId)
        commands.remove(computerId)
        usedNonces.keys.removeAll { it.startsWith("$computerId:") }
        saveComputers()
        publish()
    }

    @Synchronized
    fun pairedComputer(computerId: String): PairedComputer? = computers[computerId]

    @Synchronized
    fun verifyRequest(
        computerId: String,
        timestamp: String,
        nonce: String,
        method: String,
        requestTarget: String,
        body: ByteArray,
        signature: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val computer = computers[computerId] ?: return false
        val requestTime = timestamp.toLongOrNull() ?: return false
        if (kotlin.math.abs(nowMs - requestTime) > PcLinkProtocol.MAX_CLOCK_SKEW_MS) return false
        if (!NONCE.matches(nonce)) return false
        cleanupNonces(nowMs)
        val nonceKey = "$computerId:$nonce"
        if (usedNonces.putIfAbsent(nonceKey, nowMs) != null) return false
        val valid = PcLinkProtocol.verifySignature(
            secret = computer.secret,
            timestamp = timestamp,
            nonce = nonce,
            method = method,
            requestTarget = requestTarget,
            body = body,
            suppliedSignature = signature,
        )
        if (!valid) usedNonces.remove(nonceKey)
        return valid
    }

    @Synchronized
    fun markSeen(computerId: String, address: String, nowMs: Long = System.currentTimeMillis()) {
        val computer = computers[computerId] ?: return
        computers[computerId] = computer.copy(
            lastSeenAtMs = nowMs,
            lastKnownAddress = address,
        )
        if (nowMs - computer.lastSeenAtMs > LAST_SEEN_PERSIST_INTERVAL_MS) saveComputers()
    }

    @Synchronized
    fun updatePlayback(state: PcPlaybackState) {
        if (state.computerId !in computers) return
        if (state.active && state.videoUrl.isNotBlank()) {
            playbacks[state.computerId] = state
        } else {
            playbacks.remove(state.computerId)
        }
        publish()
    }

    @Synchronized
    fun expireStalePlaybacks(nowMs: Long = System.currentTimeMillis()) {
        var changed = false
        playbacks.entries.removeAll { (_, state) ->
            (nowMs - state.receivedAtMs > PcLinkProtocol.STATE_STALE_AFTER_MS).also {
                changed = changed || it
            }
        }
        if (changed) publish()
    }

    fun enqueueCommand(
        computerId: String,
        type: PcRemoteCommandType,
        positionMs: Long? = null,
    ): Boolean {
        if (pairedComputer(computerId) == null) return false
        val normalizedPosition = when (type) {
            PcRemoteCommandType.Seek -> positionMs?.coerceAtLeast(0L) ?: return false
            else -> null
        }
        val queue = commands.getOrPut(computerId) { mutableListOf() }
        synchronized(queue) {
            if (type == PcRemoteCommandType.Seek) {
                // A later scrub supersedes any seek that has not reached the browser yet.
                queue.removeAll { it.type == PcRemoteCommandType.Seek }
            }
            queue += PcRemoteCommand(
                sequence = commandSequence.incrementAndGet(),
                type = type,
                positionMs = normalizedPosition,
            )
            if (queue.size > MAX_PENDING_COMMANDS) {
                queue.subList(0, queue.size - MAX_PENDING_COMMANDS).clear()
            }
        }
        return true
    }

    fun commandsAfter(computerId: String, acknowledgedSequence: Long): List<PcRemoteCommand> {
        val queue = commands[computerId] ?: return emptyList()
        synchronized(queue) {
            queue.removeAll { it.sequence <= acknowledgedSequence }
            return queue.toList()
        }
    }

    private fun publish() {
        val paired = computers.values
            .sortedBy { it.name.lowercase() }
        val active = playbacks.values.maxByOrNull(PcPlaybackState::receivedAtMs)
        _snapshot.value = PcLinkSnapshot(paired, active, serverAddresses)
    }

    private fun refreshServerAddresses() {
        val addresses = currentPhysicalLanServerUrls()
        synchronized(this) {
            if (addresses == serverAddresses) return
            serverAddresses = addresses
            publish()
        }
    }

    private fun currentPhysicalLanServerUrls(): List<String> {
        val addresses = connectivityManager.allNetworks
            .mapNotNull { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                    ?: return@mapNotNull null
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    return@mapNotNull null
                }
                val physicalTransport =
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (!physicalTransport) return@mapNotNull null
                connectivityManager.getLinkProperties(network)
            }
            .flatMap { it.linkAddresses }
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .filter { address ->
                !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    address.isSiteLocalAddress
            }
            .mapNotNull { it.hostAddress }
            .distinct()
            .sorted()
            .map { address -> "http://$address:${PcLinkProtocol.PORT}" }
        return addresses.ifEmpty { PcLinkProtocol.localServerUrls() }
    }

    private fun cleanupNonces(nowMs: Long) {
        usedNonces.entries.removeAll { nowMs - it.value > NONCE_RETENTION_MS }
    }

    private fun loadComputers(): List<PairedComputer> = runCatching {
        val array = JSONArray(preferences.getString(KEY_COMPUTERS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString("id")
                val name = json.optString("name")
                val secret = json.optString("secret")
                if (id.isBlank() || name.isBlank() || secret.isBlank()) continue
                add(
                    PairedComputer(
                        id = id,
                        name = name,
                        secret = secret,
                        pairedAtMs = json.optLong("pairedAtMs"),
                        lastSeenAtMs = json.optLong("lastSeenAtMs"),
                        lastKnownAddress = json.optString("lastKnownAddress"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveComputers() {
        val array = JSONArray()
        computers.values.forEach { computer ->
            array.put(
                JSONObject()
                    .put("id", computer.id)
                    .put("name", computer.name)
                    .put("secret", computer.secret)
                    .put("pairedAtMs", computer.pairedAtMs)
                    .put("lastSeenAtMs", computer.lastSeenAtMs)
                    .put("lastKnownAddress", computer.lastKnownAddress),
            )
        }
        preferences.edit {
            putString(KEY_COMPUTERS, array.toString())
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "grayjoy_pc_link_v1"
        private const val KEY_COMPUTERS = "paired_computers"
        private const val LAST_SEEN_PERSIST_INTERVAL_MS = 60_000L
        private const val NONCE_RETENTION_MS = 3 * 60_000L
        private const val MAX_PENDING_COMMANDS = 32
        private val NONCE = Regex("[A-Za-z0-9_-]{12,100}")

        @Volatile
        private var instance: PcLinkManager? = null

        fun get(context: Context): PcLinkManager = instance ?: synchronized(this) {
            instance ?: PcLinkManager(context.applicationContext).also { instance = it }
        }
    }
}
