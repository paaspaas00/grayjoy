package com.futo.platformplayer.compose.ui

/** Keep library metadata, but never let an old/empty saved avatar hide a refreshed one. */
internal fun mergeKnownChannelArtwork(saved: List<ChannelUiModel>, refreshed: Collection<ChannelUiModel>): List<ChannelUiModel> {
    fun key(channel: ChannelUiModel) = ChannelArtworkIndex.key(channel.sourceId, channel.id)
    val fresh = refreshed.associateBy(::key)
    val seen = HashSet<String>()
    return buildList {
        saved.forEach { channel ->
            val key = key(channel)
            if (seen.add(key)) add(channel.copy(
                thumbnailUrl = fresh[key]?.thumbnailUrl?.takeIf(String::isNotBlank) ?: channel.thumbnailUrl,
            ))
        }
        refreshed.forEach { if (seen.add(key(it))) add(it) }
    }
}
