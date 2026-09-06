package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.json.JSONArray
import org.json.JSONObject

internal data class CachedHomePage(
    val videos: List<VideoUiModel>,
    val continuationId: String?,
    val hasMore: Boolean,
)

internal data class CachedHomeSnapshot(
    val selectedFeed: HomeFeedType,
    val pages: Map<HomeFeedType, CachedHomePage>,
    val pagerSessionId: String? = null,
)

/** Pager handles refer to live engine objects, not durable network cursors. */
internal fun CachedHomeSnapshot.forPagerSession(sessionId: String): CachedHomeSnapshot =
    if (pagerSessionId == sessionId) this else copy(
        pagerSessionId = sessionId,
        pages = pages.mapValues { (_, page) -> page.copy(continuationId = null) },
    )

/** A last-successful-response cache. Home is refreshed only by an explicit user/data change. */
internal class HomeCacheRepository(context: Context, profileId: String) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "grayjoy_home_cache_v1_$profileId",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): CachedHomeSnapshot? = runCatching {
        val root = preferences.getString(KEY_SNAPSHOT, null)?.let(::JSONObject)
            ?: return@runCatching null
        val pagesJson = root.optJSONObject("pages") ?: return@runCatching null
        val pages = buildMap {
            HomeFeedType.entries.forEach { feed ->
                val page = pagesJson.optJSONObject(feed.name) ?: return@forEach
                put(
                    feed,
                    CachedHomePage(
                        videos = page.optJSONArray("videos")?.toVideoList().orEmpty(),
                        continuationId = page.optString("continuationId")
                            .takeIf(String::isNotBlank),
                        hasMore = page.optBoolean("hasMore"),
                    ),
                )
            }
        }
        if (pages.isEmpty()) return@runCatching null
        val selected = runCatching {
            HomeFeedType.valueOf(root.optString("selectedFeed"))
        }.getOrDefault(HomeFeedType.Subscriptions)
        CachedHomeSnapshot(selectedFeed = selected, pages = pages)
    }.getOrNull()

    @Synchronized
    fun save(snapshot: CachedHomeSnapshot) {
        val root = JSONObject().apply {
            put("selectedFeed", snapshot.selectedFeed.name)
            put(
                "pages",
                JSONObject().apply {
                    snapshot.pages.forEach { (feed, page) ->
                        put(
                            feed.name,
                            JSONObject().apply {
                                put(
                                    "videos",
                                    JSONArray().apply {
                                        page.videos.forEach { video ->
                                            put(video.forLocalStorage().toJson())
                                        }
                                    },
                                )
                                put("continuationId", page.continuationId.orEmpty())
                                put("hasMore", page.hasMore)
                            },
                        )
                    }
                },
            )
        }
        preferences.edit().putString(KEY_SNAPSHOT, root.toString()).apply()
    }

    @Synchronized
    fun removeFeeds(feeds: Set<HomeFeedType>) {
        if (feeds.containsAll(HomeFeedType.entries)) {
            preferences.edit().remove(KEY_SNAPSHOT).apply()
            return
        }
        // Invalidation does not need thousands of VideoUiModels or a full re-encode.
        val root = runCatching {
            preferences.getString(KEY_SNAPSHOT, null)?.let(::JSONObject)
        }.getOrNull() ?: return
        val pages = root.optJSONObject("pages") ?: return
        feeds.forEach { pages.remove(it.name) }
        preferences.edit().apply {
            if (pages.length() == 0) remove(KEY_SNAPSHOT)
            else putString(KEY_SNAPSHOT, root.toString())
        }.apply()
    }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
    }
}
