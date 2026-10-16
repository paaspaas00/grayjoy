package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.VideoUiModel
import java.text.Normalizer
import java.util.Locale

private val spotifyTrackUrl = Regex("https?://open\\.spotify\\.com/(?:intl-[a-z-]+/)?track/[A-Za-z0-9]+", RegexOption.IGNORE_CASE)
private val noiseWords = setOf("official", "audio", "video", "lyrics", "lyric", "topic", "vevo", "hd", "hq")
private val variantWords = setOf("cover", "karaoke", "reaction", "remix", "live", "slowed", "reverb", "sped", "nightcore", "instrumental")

internal fun isSpotifyYoutubeBridgeTrack(video: VideoUiModel): Boolean =
    video.sourceId.equals("spotify", ignoreCase = true) &&
        spotifyTrackUrl.matches(video.contentUrl.ifBlank { video.id }.substringBefore('?'))

internal fun spotifyYoutubeSearchQuery(video: VideoUiModel): String =
    listOf(video.creator, video.title, "official audio")
        .filter(String::isNotBlank)
        .joinToString(" ")

internal fun selectSpotifyYoutubeCandidate(
    spotify: VideoUiModel,
    candidates: List<VideoUiModel>,
): VideoUiModel? = candidates.maxByOrNull { spotifyYoutubeMatchScore(spotify, it) }
    ?.takeIf { spotifyYoutubeMatchScore(spotify, it) >= 80 }

internal fun spotifyYoutubeMatchScore(spotify: VideoUiModel, youtube: VideoUiModel): Int {
    val wantedTitle = normalizedWords(spotify.title)
    val wantedArtist = normalizedWords(spotify.creator)
    val candidateTitle = normalizedWords(youtube.title)
    val candidateCreator = normalizedWords(youtube.creator)
    val searchable = candidateTitle + candidateCreator
    var score = 0

    if (wantedTitle.isNotEmpty()) {
        val matched = wantedTitle.count(searchable::contains)
        score += matched * 24
        if (matched == wantedTitle.size) score += 70
    }
    if (wantedArtist.isNotEmpty()) {
        val matched = wantedArtist.count(searchable::contains)
        score += matched * 20
        if (matched == wantedArtist.size) score += 55
    }
    if ("official" in candidateTitle || "audio" in candidateTitle || "topic" in candidateCreator) score += 20

    val wantedVariants = wantedTitle.intersect(variantWords)
    score -= (candidateTitle.intersect(variantWords) - wantedVariants).size * 55

    val targetDuration = parseDurationSeconds(spotify.duration)
    val candidateDuration = parseDurationSeconds(youtube.duration)
    if (targetDuration > 0 && candidateDuration > 0) {
        score += when (kotlin.math.abs(targetDuration - candidateDuration)) {
            in 0..2 -> 70
            in 3..5 -> 50
            in 6..12 -> 25
            in 13..25 -> 0
            else -> -70
        }
    }
    if (youtube.isLive || youtube.isShort) score -= 100
    return score
}

private fun normalizedWords(value: String): Set<String> = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .asSequence()
    .filter { it.length > 1 && it !in noiseWords }
    .toSet()

private fun parseDurationSeconds(value: String): Long {
    val parts = value.split(':').mapNotNull(String::toLongOrNull)
    if (parts.isEmpty() || parts.size > 3) return 0L
    return parts.fold(0L) { total, part -> total * 60L + part }
}
