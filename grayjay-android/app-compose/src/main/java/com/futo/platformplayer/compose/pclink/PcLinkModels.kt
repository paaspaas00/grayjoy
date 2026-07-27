package com.futo.platformplayer.compose.pclink

data class PairedComputer(
    val id: String,
    val name: String,
    val secret: String,
    val pairedAtMs: Long,
    val lastSeenAtMs: Long = 0L,
    val lastKnownAddress: String = "",
)

data class PcPlaybackState(
    val computerId: String,
    val computerName: String,
    val active: Boolean,
    val kind: PcMediaKind,
    val title: String,
    val videoTitle: String,
    val videoUrl: String,
    val playlistUrl: String,
    val artworkUrl: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val receivedAtMs: Long,
)

enum class PcMediaKind {
    Video,
    Playlist,
}

enum class PcRemoteCommandType(val wireName: String) {
    Play("play"),
    Pause("pause"),
    Toggle("toggle"),
    Previous("previous"),
    Next("next"),
    Seek("seek"),
}

data class PcRemoteCommand(
    val sequence: Long,
    val type: PcRemoteCommandType,
    val positionMs: Long? = null,
)

internal fun PcRemoteCommand.wirePayload(): Map<String, Any> = buildMap {
    put("sequence", sequence)
    put("type", type.wireName)
    positionMs?.let { put("positionMs", it) }
}

data class PcLinkSnapshot(
    val pairedComputers: List<PairedComputer> = emptyList(),
    val activePlayback: PcPlaybackState? = null,
    val serverAddresses: List<String> = emptyList(),
)

data class PcPairingPayload(
    val computerId: String,
    val computerName: String,
    val secret: String,
)
