package com.futo.platformplayer.compose.sponsorblock

import androidx.annotation.StringRes
import com.futo.platformplayer.compose.R
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class SponsorBlockCategory(
    val apiValue: String,
    @param:StringRes val labelRes: Int,
) {
    Sponsor("sponsor", R.string.sponsorblock_category_sponsor),
    SelfPromotion("selfpromo", R.string.sponsorblock_category_self_promotion),
    Interaction("interaction", R.string.sponsorblock_category_interaction),
    Intro("intro", R.string.sponsorblock_category_intro),
    Outro("outro", R.string.sponsorblock_category_outro),
    Preview("preview", R.string.sponsorblock_category_preview),
    MusicOfftopic("music_offtopic", R.string.sponsorblock_category_music_offtopic),
    Filler("filler", R.string.sponsorblock_category_filler),
    ;

    companion object {
        val defaultCategories: Set<SponsorBlockCategory> = setOf(Sponsor)

        fun fromApiValue(value: String): SponsorBlockCategory? =
            entries.firstOrNull { it.apiValue == value }
    }
}

data class SponsorBlockRule(
    val enabled: Boolean = true,
    val categories: Set<SponsorBlockCategory> = SponsorBlockCategory.defaultCategories,
)

data class SponsorBlockSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val category: SponsorBlockCategory,
)

fun effectiveSponsorBlockRule(
    global: SponsorBlockRule,
    channelOverride: SponsorBlockRule?,
    videoOverride: SponsorBlockRule?,
): SponsorBlockRule = videoOverride ?: channelOverride ?: global

fun SponsorBlockSegment.contains(positionMs: Long): Boolean =
    positionMs >= startMs && positionMs < endMs

fun sponsorSegmentToSkip(
    segments: List<SponsorBlockSegment>,
    positionMs: Long,
    durationMs: Long,
    manuallyAllowedIds: Set<String>,
    lastSkippedId: String?,
): SponsorBlockSegment? {
    if (positionMs < 0L || durationMs <= 0L) return null
    return segments.firstOrNull {
        it.id !in manuallyAllowedIds &&
            it.id != lastSkippedId &&
            it.contains(positionMs) &&
            it.endMs <= durationMs + 1_000L &&
            it.endMs - positionMs >= 250L
    }
}

internal fun parseSponsorBlockSegments(
    payload: String,
    selectedCategories: Set<SponsorBlockCategory>,
): List<SponsorBlockSegment> {
    val result = mutableListOf<SponsorBlockSegment>()
    val array = JSONArray(payload)
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        if (item.optString("actionType", "skip") != "skip") continue
        val category = SponsorBlockCategory.fromApiValue(item.optString("category")) ?: continue
        if (category !in selectedCategories) continue
        val bounds = item.optJSONArray("segment") ?: continue
        if (bounds.length() < 2) continue
        val startSeconds = bounds.optDouble(0, Double.NaN)
        val endSeconds = bounds.optDouble(1, Double.NaN)
        if (!startSeconds.isFinite() || !endSeconds.isFinite()) continue
        val startMs = (startSeconds * 1_000.0).toLong()
        val endMs = (endSeconds * 1_000.0).toLong()
        if (startMs < 0L || endMs <= startMs) continue
        result += SponsorBlockSegment(
            id = item.optString("UUID").ifBlank { "${category.apiValue}:$startMs:$endMs" },
            startMs = startMs,
            endMs = endMs,
            category = category,
        )
    }
    return result.sortedBy(SponsorBlockSegment::startMs)
}

internal fun youtubeVideoId(urlOrId: String): String? {
    val raw = urlOrId.trim()
    if (raw.matches(Regex("[A-Za-z0-9_-]{11}"))) return raw
    val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase()
    if (host == "youtu.be" || host.endsWith(".youtu.be")) {
        return uri.path.trim('/').substringBefore('/').takeIf { it.length == 11 }
    }
    if (!host.endsWith("youtube.com")) return null
    val queryId = uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { part ->
        val pieces = part.split('=', limit = 2)
        pieces.getOrNull(1)?.takeIf { pieces.firstOrNull() == "v" }
    }
    if (queryId?.length == 11) return queryId
    return uri.path.trim('/').split('/').let { parts ->
        parts.getOrNull(1).takeIf {
            parts.firstOrNull() in setOf("shorts", "live", "embed") && it?.length == 11
        }
    }
}

class SponsorBlockClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class CacheEntry(val storedAtMs: Long, val segments: List<SponsorBlockSegment>)

    private val cache = linkedMapOf<String, CacheEntry>()

    fun load(videoId: String, categories: Set<SponsorBlockCategory>): List<SponsorBlockSegment> {
        if (categories.isEmpty()) return emptyList()
        val categoryKey = categories.map(SponsorBlockCategory::apiValue).sorted().joinToString(",")
        val key = "$videoId|$categoryKey"
        synchronized(cache) {
            cache[key]?.takeIf { clock() - it.storedAtMs < CACHE_TTL_MS }?.let { return it.segments }
        }
        val url = API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("videoID", videoId)
            .addQueryParameter(
                "categories",
                JSONArray(categories.map(SponsorBlockCategory::apiValue).sorted()).toString(),
            )
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        var lastFailure: IOException? = null
        var segments: List<SponsorBlockSegment>? = null
        repeat(2) { attempt ->
            if (segments != null) return@repeat
            try {
                segments = httpClient.newCall(request).execute().use { response ->
                    if (response.code == 404) emptyList()
                    else {
                        if (response.code >= 500) throw IOException("SponsorBlock HTTP ${response.code}")
                        check(response.isSuccessful) { "SponsorBlock HTTP ${response.code}" }
                        parseSponsorBlockSegments(response.body.string(), categories)
                    }
                }
            } catch (error: IOException) {
                lastFailure = error
                if (attempt == 1) throw error
            }
        }
        val loadedSegments = segments ?: throw lastFailure ?: IOException("SponsorBlock request failed")
        synchronized(cache) {
            cache[key] = CacheEntry(clock(), loadedSegments)
            while (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
        }
        return loadedSegments
    }

    companion object {
        private const val API_URL = "https://sponsor.ajay.app/api/skipSegments"
        private const val CACHE_TTL_MS = 12 * 60 * 60 * 1_000L
        private const val MAX_CACHE_ENTRIES = 100
    }
}
