package com.futo.platformplayer.compose.voice

import com.futo.platformplayer.compose.ui.VideoUiModel

internal data class VoiceArtistSession(
    val profileId: String,
    val artist: String,
    val lastVideoId: String,
    val playedIds: Set<String>,
    val candidates: List<VideoUiModel> = emptyList(),
    val cachedAtMs: Long = 0,
    val playedTracks: Set<String> = emptySet(),
) {
    fun applies(profile: String, currentId: String?, pendingId: String?) =
        profileId == profile && (lastVideoId == currentId || lastVideoId == pendingId)
    fun freshCandidates(now: Long) = candidates.takeIf { now - cachedAtMs in 0..120_000 }.orEmpty()
}

internal fun cleanVoiceArtist(name: String): String = name.trim().take(120)
    .replace(Regex("(?i)\\s*[-–]\\s*topic$|\\s*official(?: channel)?$|vevo$"), "").trim()

internal fun inferVoiceArtist(video: VideoUiModel?): String? {
    if (video == null || video.sourceId != "youtube" || video.isMusic == false) return null
    if (Regex("(?i)(?:[-–]\\s*topic|vevo|official(?: channel)?)$").containsMatchIn(video.creator))
        return cleanVoiceArtist(video.creator).takeIf { it.length in 2..100 }
    // Titles occur in both artist-song and song-artist order. Require corroborating uploader metadata.
    if (video.isMusic == true) {
        val author = cleanVoiceArtist(video.creator)
        return video.title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
            .takeIf { it.size == 2 }?.map(String::trim)?.firstOrNull {
                it.length in 2..80 && VoiceNameMatcher.normalized(it) == VoiceNameMatcher.normalized(author)
            }
    }
    return null
}

internal fun artistVideos(artist: String, videos: List<VideoUiModel>): List<VideoUiModel> {
    val wanted = VoiceNameMatcher.normalized(artist)
    fun matches(label: String): Boolean {
        val normalized = VoiceNameMatcher.normalized(label)
        if (normalized == wanted || " $normalized ".contains(" $wanted ")) return true
        return VoiceNameMatcher.rank(artist, listOf(VoiceNameCandidate("", label)), titleWindows = true)
            .firstOrNull()?.score?.let { it >= .89 } == true
    }
    return videos.distinctBy { it.id }.filter { video ->
        video.sourceId == "youtube" && video.isAvailable && !video.isLive && video.isMusic != false &&
            !Regex("(?i)\\b(?:reaction|reazione|tutorial|lesson|lezione|interview|intervista)\\b").containsMatchIn(video.title) &&
            (matches(cleanVoiceArtist(video.creator)) || matches(video.title))
    }.sortedWith(compareByDescending<VideoUiModel> { matches(cleanVoiceArtist(it.creator)) }
        .thenByDescending { it.viewCount })
}

internal fun artistTrackKey(video: VideoUiModel, artist: String): String {
    val annotation = Regex("(?i)\\b(?:official|video|audio|lyrics|lyric|hd|4k|remastered|remaster|visualizer|hq|live)\\b")
    val title = VoiceNameMatcher.normalized(video.title.replace(Regex("\\([^)]*\\)|\\[[^]]*]")) {
        if (annotation.containsMatchIn(it.value)) " " else it.value
    })
    val key = title.removePrefix(VoiceNameMatcher.normalized(artist)).trim()
        .replace(Regex("\\s+(?:official (?:music )?(?:video|audio|lyric video)|lyrics|lyric video|hd|4k|remastered(?: \\d{4})?|visualizer|hq)$"), "")
        .replace(Regex("\\s+"), " ").trim()
    return key.ifBlank { title }
}

internal fun chooseArtistVideo(videos: List<VideoUiModel>, seen: Set<String>, currentId: String?, allowRepeat: Boolean,
    artist: String = "", seenTracks: Set<String> = emptySet(), currentTrack: String? = null): VideoUiModel? {
    val available = videos.filter { it.id != currentId && (currentTrack == null || artistTrackKey(it, artist) != currentTrack) }
    return available.firstOrNull { it.id !in seen && artistTrackKey(it, artist) !in seenTracks }
        ?: available.firstOrNull { allowRepeat }
}
