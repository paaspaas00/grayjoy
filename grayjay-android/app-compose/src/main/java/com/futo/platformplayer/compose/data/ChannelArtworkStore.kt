package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.ui.ChannelUiModel
import org.json.JSONArray
import org.json.JSONObject

internal class ChannelArtworkStore(context: Context, profile: String) {
    private val prefs = context.getSharedPreferences("grayjoy_channel_artwork_v1_$profile", Context.MODE_PRIVATE)
    @Synchronized fun load(): List<Pair<ChannelUiModel, Long>> = runCatching {
        val data = JSONArray(prefs.getString("channels", "[]"))
        (0 until data.length()).map { index -> val value = data.getJSONObject(index)
            ChannelUiModel(value.getString("id"), value.getString("name"), value.getString("source"), "", 0, "", "", value.getString("image")) to value.optLong("updated")
        }
    }.getOrDefault(emptyList())
    @Synchronized fun save(channel: ChannelUiModel, updated: Long) {
        if (channel.thumbnailUrl.isBlank()) return
        val channels = load().filterNot { it.first.id == channel.id && it.first.sourceId == channel.sourceId }.plus(channel to updated).takeLast(1024)
        prefs.edit().putString("channels", JSONArray().apply { channels.forEach { (item, time) -> put(JSONObject()
            .put("id", item.id).put("source", item.sourceId).put("name", item.name).put("image", item.thumbnailUrl).put("updated", time)) } }.toString()).apply()
    }
    fun clear() { prefs.edit().clear().apply() }
}
