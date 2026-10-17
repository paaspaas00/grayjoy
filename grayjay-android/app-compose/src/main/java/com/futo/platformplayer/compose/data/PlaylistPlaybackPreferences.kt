package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import org.json.JSONArray
import org.json.JSONObject

/** Small per-profile playback preferences; remote playlist entries retain no stream URLs. */
internal class PlaylistPlaybackPreferences(context: Context, profileId: String) {
    private val preferences = context.getSharedPreferences("playlist_playback_$profileId", Context.MODE_PRIVATE)
    fun shuffledIds(): Set<String> = preferences.getStringSet("shuffled", emptySet()).orEmpty().toSet()
    fun setShuffled(id: String, enabled: Boolean) {
        preferences.edit().putStringSet("shuffled", shuffledIds().toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }).apply()
    }
    fun recent(): List<PlaylistUiModel> = runCatching {
        val array = JSONArray(preferences.getString("recent", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            PlaylistUiModel(item.getString("id"), item.getString("title"), item.optString("description"),
                emptyList(), sourceId = item.optString("sourceId"), thumbnailUrl = item.optString("thumbnailUrl"),
                videoCount = item.optInt("videoCount", -1))
        }.distinctBy(PlaylistUiModel::id).take(5)
    }.getOrDefault(emptyList())
    fun record(playlist: PlaylistUiModel) {
        val recent = (listOf(playlist) + recent()).distinctBy(PlaylistUiModel::id).take(5)
        preferences.edit().putString("recent", JSONArray(recent.map { entry ->
            JSONObject().put("id", entry.id).put("title", entry.title).put("description", entry.description)
                .put("sourceId", entry.sourceId).put("thumbnailUrl", entry.thumbnailUrl).put("videoCount", entry.videoCount)
        }).toString()).apply()
    }
}
