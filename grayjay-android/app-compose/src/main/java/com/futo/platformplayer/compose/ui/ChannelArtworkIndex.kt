package com.futo.platformplayer.compose.ui

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

internal class ChannelArtworkIndex(channels: List<ChannelUiModel>) {
    private val withImages = channels.filter { it.thumbnailUrl.isNotBlank() }.distinctBy { "${it.sourceId}|${it.id}" }
    private val references = buildMap { withImages.forEach { channel -> putIfAbsent(key(channel.sourceId, channel.id), channel) } }
    private val names = withImages.groupBy { key(it.sourceId, it.name.lowercase(Locale.ROOT)) }.filterValues { list -> list.map { it.thumbnailUrl }.distinct().size == 1 }.mapValues { it.value.first() }
    fun find(video: VideoUiModel): String? = sequenceOf(video.authorUrl, video.channelId)
        .filter(String::isNotBlank).mapNotNull { references[key(video.sourceId, it)]?.thumbnailUrl }.firstOrNull()
        ?: names[key(video.sourceId, video.creator.lowercase(Locale.ROOT))]?.thumbnailUrl
    companion object {
        private fun key(source: String, reference: String): String {
            val value = reference.trim().trimEnd('/')
            val uri = runCatching { java.net.URI(value) }.getOrNull()
            val segments = uri?.path.orEmpty().split('/').filter(String::isNotEmpty)
            val index = segments.indexOf("channel")
            val canonical = when {
                uri?.host != null && index >= 0 && index + 1 < segments.size ->
                    "channel:${segments[index + 1]}"
                source.equals("youtube", ignoreCase = true) &&
                    value.startsWith("UC") && '/' !in value -> "channel:$value"
                else -> value
            }
            return "${source.lowercase(Locale.ROOT)}|$canonical"
        }
    }
}

internal val LocalChannelArtworkIndex = compositionLocalOf { ChannelArtworkIndex(emptyList()) }
internal val LocalChannelArtworkRequest = compositionLocalOf<(String) -> Unit> { {} }
