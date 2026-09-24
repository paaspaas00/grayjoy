package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.VideoUiModel

/** Old URL-only imports defaulted to YouTube even when the source was not known. */
internal fun repairLegacyVideoSource(video: VideoUiModel): VideoUiModel {
    if (video.sourceId.isNotBlank() && !video.sourceId.equals("youtube", true)) return video
    val contentUrl = video.contentUrl.ifBlank { video.id }
    val source = sourceIdHintForUrl(contentUrl) ?: sourceIdHintForUrl(video.shareUrl) ?: return video
    if (source.equals(video.sourceId, true)) return video
    val canonicalUrl = if (contentUrl.startsWith("lbry://", true) &&
        video.shareUrl.startsWith("https://", true) && sourceIdHintForUrl(video.shareUrl) == source
    ) video.shareUrl else contentUrl
    return video.copy(
        sourceId = source,
        sourceName = source.replaceFirstChar(Char::uppercase),
        sourceIconUrl = "",
        contentUrl = canonicalUrl,
    )
}

internal fun isYoutubeContentReference(value: String): Boolean =
    sourceIdHintForUrl(value) == "youtube" || value.matches(Regex("[A-Za-z0-9_-]{11}"))
