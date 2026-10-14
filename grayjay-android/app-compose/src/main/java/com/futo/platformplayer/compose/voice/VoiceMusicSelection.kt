package com.futo.platformplayer.compose.voice

import com.futo.platformplayer.compose.ui.VideoUiModel

internal fun recentVoiceMusic(library: List<VideoUiModel>, downloads: List<VideoUiModel>,
    localOnly: Boolean, currentId: String?): List<VideoUiModel> {
    val byId = downloads.associateBy { it.id }
    val recent = library.asSequence().filter { it.isMusic == true && it.lastWatchedAt > 0L && it.isAvailable && !it.isLive }
        .sortedByDescending { it.lastWatchedAt }.take(100).mapNotNull {
            if (localOnly) byId[it.id] else byId[it.id] ?: it
        }.toList()
    return recent.filter { it.id != currentId }.ifEmpty { recent }
}
