package com.futo.platformplayer.compose.casting

import android.content.Context
import android.os.Build
import androidx.media3.common.MimeTypes
import com.futo.platformplayer.compose.BuildConfig
import com.futo.platformplayer.compose.ui.ChromecastDeviceUiModel
import com.futo.platformplayer.compose.ui.ChromecastUiState
import com.futo.platformplayer.compose.ui.CastProtocolUi
import com.futo.platformplayer.compose.ui.PlaybackUiState
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fcast.sender_sdk.ApplicationInfo
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceDiscovererEventHandler
import org.fcast.sender_sdk.DeviceEventHandler
import org.fcast.sender_sdk.DeviceFeature
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.EventSubscription
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.KeyEvent
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaEvent
import org.fcast.sender_sdk.MediaItemEventType
import org.fcast.sender_sdk.Metadata
import org.fcast.sender_sdk.NsdDeviceDiscoverer
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.Source
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** Chromecast and FCast sender backed by the same FUTO sender SDK used by legacy Grayjay. */
internal class ChromecastManager(context: Context) {
    private data class PendingMedia(
        val video: VideoUiModel,
        val playback: PlaybackUiState,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val castContext = CastContext()
    private val devices = ConcurrentHashMap<String, DeviceInfo>()
    private val server = CastHttpServer()
    private val _state = MutableStateFlow(ChromecastUiState())
    val state = _state.asStateFlow()

    private var discoverer: NsdDeviceDiscoverer? = null
    private var activeDevice: CastingDevice? = null
    private var activeDeviceId: String? = null
    private var activeProtocol: CastProtocolUi? = null
    private var localAddress: InetAddress? = null
    private var pendingMedia: PendingMedia? = null
    private var tickerJob: Job? = null
    private var loadJob: Job? = null
    private val loadMutex = Mutex()
    private var remotePositionSeconds = 0.0
    private var remotePositionChangedAt = 0L
    private var remotePlaying = false
    var onMediaEnded: (() -> Unit)? = null

    fun startDiscovery() {
        if (discoverer != null) {
            _state.update { it.copy(isDiscovering = true) }
            return
        }
        _state.update { it.copy(isDiscovering = true, errorMessage = null) }
        discoverer = NsdDeviceDiscoverer(
            appContext,
            object : DeviceDiscovererEventHandler {
                override fun deviceAvailable(deviceInfo: DeviceInfo) = updateDevice(deviceInfo)

                override fun deviceChanged(deviceInfo: DeviceInfo) = updateDevice(deviceInfo)

                override fun deviceRemoved(deviceName: String) {
                    devices.entries
                        .filter { it.value.name == deviceName }
                        .forEach { devices.remove(it.key) }
                    publishDevices()
                }
            },
        )
    }

    fun connect(deviceId: String, video: VideoUiModel, playback: PlaybackUiState) {
        val info = devices[deviceId] ?: run {
            _state.update { it.copy(errorMessage = "Chromecast is no longer available.") }
            return
        }
        disconnectInternal(stopRemotePlayback = false, publishState = false)
        pendingMedia = PendingMedia(video, playback)
        activeDeviceId = deviceId
        activeProtocol = info.protocol.toUiProtocol()
        _state.update {
            it.copy(
                activeDeviceId = deviceId,
                activeDeviceName = info.name,
                activeProtocol = activeProtocol,
                isConnecting = true,
                isConnected = false,
                errorMessage = null,
            )
        }
        val device = castContext.createDeviceFromInfo(info)
        activeDevice = device
        val eventHandler = createEventHandler(deviceId, info.name, device)
        runCatching {
            device.connect(
                ApplicationInfo(
                    "Grayjoy Android",
                    BuildConfig.VERSION_NAME,
                    "${Build.MANUFACTURER} ${Build.MODEL}",
                ),
                eventHandler,
                1_000uL,
            )
        }.onFailure(::publishFailure)
    }

    fun cast(video: VideoUiModel, playback: PlaybackUiState) {
        pendingMedia = PendingMedia(video, playback)
        if (_state.value.isConnected) loadPendingMedia()
    }

    fun togglePlayback() {
        val device = activeDevice ?: return
        runCatching {
            if (_state.value.isPlaying) device.pausePlayback() else device.resumePlayback()
        }.onFailure(::publishFailure)
    }

    fun seekTo(positionMs: Long) {
        activeDevice?.let { device ->
            val targetMs = positionMs.coerceIn(
                0L,
                _state.value.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
            )
            remotePositionSeconds = targetMs / 1_000.0
            remotePositionChangedAt = System.currentTimeMillis()
            _state.update { it.copy(positionMs = targetMs) }
            runCatching { device.seek(targetMs / 1_000.0) }
                .onFailure(::publishFailure)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        activeDevice
            ?.takeIf { it.supportsFeature(DeviceFeature.SET_SPEED) }
            ?.let { device -> runCatching { device.changeSpeed(speed.toDouble()) }.onFailure(::publishFailure) }
    }

    fun disconnect(stopRemotePlayback: Boolean = true) {
        disconnectInternal(stopRemotePlayback, publishState = true)
    }

    fun release() {
        disconnectInternal(stopRemotePlayback = false, publishState = false)
        discoverer = null
        server.stop()
        scope.cancel()
    }

    private fun updateDevice(deviceInfo: DeviceInfo) {
        if (deviceInfo.protocol != ProtocolType.CHROMECAST && deviceInfo.protocol != ProtocolType.F_CAST) return
        devices[deviceInfo.deviceId()] = deviceInfo
        publishDevices()
    }

    private fun publishDevices() {
        _state.update { current ->
            current.copy(
                devices = devices.values
                    .sortedBy { it.name.lowercase() }
                    .map {
                        ChromecastDeviceUiModel(
                            id = it.deviceId(),
                            name = it.name,
                            protocol = it.protocol.toUiProtocol(),
                        )
                    },
            )
        }
    }

    private fun createEventHandler(
        deviceId: String,
        deviceName: String,
        device: CastingDevice,
    ): DeviceEventHandler = object : DeviceEventHandler {
        override fun connectionStateChanged(state: DeviceConnectionState) {
            when (state) {
                is DeviceConnectionState.Connected -> {
                    if (device !== activeDevice) return
                    localAddress = state.localAddr.toInetAddress()
                    if (device.supportsFeature(DeviceFeature.MEDIA_EVENT_SUBSCRIPTION)) {
                        runCatching { device.subscribeEvent(EventSubscription.MediaItemEnd) }
                    }
                    _state.update {
                        it.copy(
                            activeDeviceId = deviceId,
                            activeDeviceName = deviceName,
                            activeProtocol = activeProtocol,
                            isConnecting = false,
                            isConnected = true,
                            errorMessage = null,
                        )
                    }
                    loadPendingMedia()
                    startTicker()
                }
                DeviceConnectionState.Connecting,
                DeviceConnectionState.Reconnecting,
                -> _state.update { it.copy(isConnecting = true, isConnected = false) }
                DeviceConnectionState.Disconnected -> {
                    if (device !== activeDevice) return
                    activeDevice = null
                    activeDeviceId = null
                    activeProtocol = null
                    localAddress = null
                    pendingMedia = null
                    tickerJob?.cancel()
                    tickerJob = null
                    server.clearRoutes()
                    _state.update {
                        it.copy(
                            activeDeviceId = null,
                            activeDeviceName = null,
                            activeProtocol = null,
                            isConnecting = false,
                            isConnected = false,
                            isPlaying = false,
                        )
                    }
                }
            }
        }

        override fun playbackStateChanged(state: PlaybackState) {
            if (device !== activeDevice) return
            val now = System.currentTimeMillis()
            if (remotePlaying) {
                remotePositionSeconds += (now - remotePositionChangedAt).coerceAtLeast(0L) / 1_000.0
            }
            remotePlaying = state == PlaybackState.PLAYING
            remotePositionChangedAt = now
            _state.update {
                it.copy(
                    isPlaying = remotePlaying,
                    positionMs = (remotePositionSeconds * 1_000.0).toLong().coerceAtLeast(0L),
                )
            }
        }

        override fun timeChanged(time: Double) {
            if (device !== activeDevice) return
            remotePositionSeconds = time.coerceAtLeast(0.0)
            remotePositionChangedAt = System.currentTimeMillis()
            _state.update { it.copy(positionMs = (remotePositionSeconds * 1_000.0).toLong()) }
        }

        override fun durationChanged(duration: Double) {
            if (device !== activeDevice) return
            _state.update { it.copy(durationMs = (duration.coerceAtLeast(0.0) * 1_000.0).toLong()) }
        }

        override fun volumeChanged(volume: Double) = Unit

        override fun speedChanged(speed: Double) = Unit

        override fun sourceChanged(source: Source) = Unit

        override fun keyEvent(event: KeyEvent) = Unit

        override fun mediaEvent(event: MediaEvent) {
            if (device === activeDevice && event.type == MediaItemEventType.END) onMediaEnded?.invoke()
        }

        override fun playbackError(message: String) {
            if (device === activeDevice) publishFailure(IllegalStateException(message))
        }
    }

    private fun loadPendingMedia() {
        val device = activeDevice ?: return
        val address = localAddress ?: return
        val pending = pendingMedia ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                loadMutex.withLock {
                    ensureActive()
                val video = pending.video.selectedCastVariant(pending.playback.selectedVideoQuality)
                require(!video.isDrmProtected) { "DRM-protected videos cannot be cast." }
                val contentType = video.castContentType()
                val sourceUrl = when {
                    video.playbackManifest.isNotBlank() -> server.serveDash(
                        manifest = video.playbackManifest,
                        localAddress = address,
                        dataSourceFactory = video.playbackDataSourceFactory,
                        requestHeaders = video.playbackRequestHeaders,
                    )
                    contentType == MimeTypes.APPLICATION_M3U8 -> server.serveHls(
                        upstreamUrl = video.playbackUrl,
                        localAddress = address,
                        dataSourceFactory = video.playbackDataSourceFactory,
                        requestHeaders = video.playbackRequestHeaders,
                    )
                    else -> server.serveProgressive(
                        upstreamUrl = video.playbackUrl,
                        contentType = contentType,
                        localAddress = address,
                        dataSourceFactory = video.playbackDataSourceFactory,
                        requestHeaders = video.playbackRequestHeaders,
                    )
                }
                ensureActive()
                if (device !== activeDevice) return@withLock
                require(sourceUrl.isNotBlank()) { "This source returned no cast-compatible stream." }
                val resumeSeconds = if (
                    activeProtocol == CastProtocolUi.Chromecast &&
                    !video.isLive &&
                    pending.playback.positionMs <= 0L
                ) {
                    // Legacy Grayjay works around Chromecast ignoring an exact zero start offset.
                    0.1
                } else {
                    pending.playback.positionMs.coerceAtLeast(0L) / 1_000.0
                }
                device.load(
                    LoadRequest.Video(
                        contentType = contentType,
                        url = sourceUrl,
                        resumePosition = resumeSeconds,
                        speed = pending.playback.playbackSpeed.toDouble(),
                        volume = null,
                        metadata = Metadata(video.title, video.thumbnailUrl.takeIf(String::isNotBlank)),
                        requestHeaders = null,
                    ),
                )
                remotePositionSeconds = resumeSeconds
                remotePositionChangedAt = System.currentTimeMillis()
                remotePlaying = true
                pendingMedia = null
                _state.update {
                    it.copy(
                        isPlaying = true,
                        positionMs = (resumeSeconds * 1_000.0).toLong(),
                        durationMs = pending.playback.durationMs,
                        errorMessage = null,
                    )
                }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                publishFailure(error)
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val state = _state.value
                if (state.isConnected && remotePlaying) {
                    val elapsed = (System.currentTimeMillis() - remotePositionChangedAt).coerceAtLeast(0L)
                    val position = (remotePositionSeconds * 1_000.0).toLong() + elapsed
                    _state.update {
                        it.copy(positionMs = position.coerceAtMost(it.durationMs.takeIf { d -> d > 0L } ?: Long.MAX_VALUE))
                    }
                }
                delay(500L)
            }
        }
    }

    private fun disconnectInternal(stopRemotePlayback: Boolean, publishState: Boolean) {
        val device = activeDevice
        activeDevice = null
        activeDeviceId = null
        activeProtocol = null
        localAddress = null
        pendingMedia = null
        tickerJob?.cancel()
        tickerJob = null
        loadJob?.cancel()
        loadJob = null
        if (device != null) {
            if (stopRemotePlayback) runCatching(device::stopPlayback)
            runCatching(device::disconnect)
        }
        server.clearRoutes()
        if (publishState) {
            _state.update {
                it.copy(
                    activeDeviceId = null,
                    activeDeviceName = null,
                    activeProtocol = null,
                    isConnecting = false,
                    isConnected = false,
                    isPlaying = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun publishFailure(error: Throwable) {
        val message = error.localizedMessage ?: "Cast connection failed."
        disconnectInternal(stopRemotePlayback = false, publishState = false)
        _state.update {
            it.copy(
                activeDeviceId = null,
                activeDeviceName = null,
                activeProtocol = null,
                isConnecting = false,
                isConnected = false,
                isPlaying = false,
                errorMessage = message,
            )
        }
    }
}

private fun DeviceInfo.deviceId(): String = "${protocol.name}:$name"

private fun ProtocolType.toUiProtocol(): CastProtocolUi = when (this) {
    ProtocolType.CHROMECAST -> CastProtocolUi.Chromecast
    ProtocolType.F_CAST -> CastProtocolUi.FCast
}

private fun IpAddr.toInetAddress(): InetAddress = when (this) {
    is IpAddr.V4 -> Inet4Address.getByAddress(
        byteArrayOf(o1.toByte(), o2.toByte(), o3.toByte(), o4.toByte()),
    )
    is IpAddr.V6 -> Inet6Address.getByAddress(
        byteArrayOf(
            o1.toByte(), o2.toByte(), o3.toByte(), o4.toByte(),
            o5.toByte(), o6.toByte(), o7.toByte(), o8.toByte(),
            o9.toByte(), o10.toByte(), o11.toByte(), o12.toByte(),
            o13.toByte(), o14.toByte(), o15.toByte(), o16.toByte(),
        ),
    )
}

private fun VideoUiModel.selectedCastVariant(height: Int?): VideoUiModel {
    val variant = height?.let { selected -> qualityVariants.firstOrNull { it.height == selected } }
        ?: return this
    return copy(
        playbackUrl = variant.playbackUrl,
        playbackMimeType = variant.playbackMimeType,
        playbackManifest = variant.playbackManifest,
        playbackRequestHeaders = variant.playbackRequestHeaders.ifEmpty { playbackRequestHeaders },
        playbackDataSourceFactory = variant.playbackDataSourceFactory,
    )
}

private fun VideoUiModel.castContentType(): String = when {
    playbackManifest.isNotBlank() -> MimeTypes.APPLICATION_MPD
    playbackMimeType.isNotBlank() -> playbackMimeType
    playbackAudioOnly && playbackUrl.substringBefore('?').endsWith(".mp3", ignoreCase = true) -> MimeTypes.AUDIO_MPEG
    playbackAudioOnly -> MimeTypes.AUDIO_MP4
    playbackUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
    playbackUrl.substringBefore('?').endsWith(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
    else -> MimeTypes.VIDEO_MP4
}
