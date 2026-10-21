package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.ui.ChannelUiModel
import org.json.JSONArray
import org.json.JSONObject
import com.google.gson.JsonParser

internal class ChannelArtworkStore(context: Context, profile: String) {
    private val prefs = context.getSharedPreferences("grayjoy_channel_artwork_v1_$profile", Context.MODE_PRIVATE)
    @Synchronized fun load(): List<Pair<ChannelUiModel, Long>> =
        parseChannelArtworkRecords(runCatching { prefs.getString("channels", "[]") }.getOrNull().orEmpty())
    @Synchronized fun save(channel: ChannelUiModel, updated: Long) {
        if (channel.thumbnailUrl.isBlank()) return
        val channels = load().filterNot { it.first.id == channel.id && it.first.sourceId == channel.sourceId }.plus(channel to updated).takeLast(1024)
        prefs.edit().putString("channels", JSONArray().apply { channels.forEach { (item, time) -> put(JSONObject()
            .put("id", item.id).put("source", item.sourceId).put("name", item.name).put("image", item.thumbnailUrl).put("updated", time)) } }.toString()).apply()
    }
    fun clear() { prefs.edit().clear().apply() }
}

internal fun parseChannelArtworkRecords(raw: String): List<Pair<ChannelUiModel, Long>> {
    val data = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return emptyList()
    return data.mapNotNull { element -> runCatching {
        if (!element.isJsonObject) return@runCatching null
        val value = element.asJsonObject
        fun text(key: String): String = value.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
        val id = text("id").takeIf(String::isNotBlank) ?: return@runCatching null
        val source = text("source").takeIf(String::isNotBlank) ?: return@runCatching null
        val image = text("image").takeIf(String::isNotBlank) ?: return@runCatching null
        val updated = runCatching { value.get("updated")?.asLong }.getOrNull()?.coerceAtLeast(0L) ?: 0L
        ChannelUiModel(id, text("name"), source, "", 0, "", "", image) to updated
    }.getOrNull() }
}
