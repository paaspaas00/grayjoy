package com.futo.platformplayer.backend

import java.util.Locale
import org.jsoup.Jsoup

/** Null means absent/unrecognized metadata, not a non-music video. No title heuristics. */
internal fun youtubeMusicClassification(category: String?): Boolean? =
    when (category?.trim()?.lowercase(Locale.ROOT)) {
        "music" -> true
        "film & animation", "autos & vehicles", "pets & animals", "sports", "travel & events",
        "gaming", "people & blogs", "comedy", "entertainment", "news & politics",
        "howto & style", "education", "science & technology", "nonprofits & activism" -> false
        else -> null
    }

internal fun youtubeMusicClassificationFromWatchPage(html: String): Boolean? {
    val head = Jsoup.parse(html).head()
    return youtubeMusicClassification(head.selectFirst("meta[itemprop=genre]")?.attr("content"))
        ?: youtubeMusicClassification(YouTubeStoryboardParser.extractJsonObjectString(
            html, "\"playerMicroformatRenderer\"", "category",
        ))
}

/** Preserve metadata already fetched by the official source; adds no requests or stream work. */
internal fun String.withYoutubeMusicMetadata(): String = this + "\n" + YOUTUBE_MUSIC_METADATA_BRIDGE

internal val YOUTUBE_MUSIC_METADATA_BRIDGE = """
    ;(() => {
        try {
            if (typeof extractVideoPlayerData_VideoDetails !== "function") return;
            const original = extractVideoPlayerData_VideoDetails;
            extractVideoPlayerData_VideoDetails = function(playerData) {
                const result = original.apply(this, arguments);
                try {
                    if (result && typeof result === "object") {
                        const category = playerData?.microformat?.playerMicroformatRenderer?.category;
                        result.__grayjoyYoutubeCategory = typeof category === "string" ? category : null;
                    }
                } catch (_) { /* Optional metadata must never interrupt playback. */ }
                return result;
            };
        } catch (_) { /* Some source versions expose a read-only extractor binding. */ }
    })();
""".trimIndent()
