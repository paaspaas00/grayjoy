package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.VideoUiModel

/** Keep content identity and library metadata, but never reuse a transient stream when resolving. */
internal fun VideoUiModel.onlinePlaybackInput(): VideoUiModel = copy(
    isDownloaded = false,
    playbackFromDownload = false,
    playbackAudioOnly = false,
    playbackCacheNamespace = "",
    audioCacheNamespace = "",
    playbackStreamKeys = emptyList(),
    audioStreamKeys = emptyList(),
    playbackUrl = "",
    playbackMimeType = "",
    playbackManifest = "",
    audioUrl = "",
    audioRequestHeaders = emptyMap(),
    audioDataSourceFactory = null,
    playbackRequestHeaders = emptyMap(),
    playbackDataSourceFactory = null,
    subtitleTracks = emptyList(),
    qualityVariants = emptyList(),
    audioQualityVariants = emptyList(),
)
