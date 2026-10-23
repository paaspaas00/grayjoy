package com.futo.platformplayer.compose.data

import android.content.Context
import org.json.JSONObject
import com.futo.platformplayer.compose.ui.DownloadMediaType

/** Explicit read grants created by an opt-in split; originals are never deleted by the recipient. */
internal class OfflineProfileLinks(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("grayjoy_offline_profile_links", Context.MODE_PRIVATE)
    private val cached = mutableMapOf<String, Pair<String, Map<String, String>>>()
    fun owners(profile: String): Map<String, String> = synchronized(lock) {
        val raw = runCatching { prefs.getString(profile, "{}") }.getOrNull().orEmpty()
        cached[profile]?.takeIf { it.first == raw }?.let { return@synchronized it.second }
        runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().mapNotNull { id ->
                json.optString(id).takeIf { it != profile && it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }?.let { id to it }
            }.toMap()
        }.getOrDefault(emptyMap()).also { cached[profile] = raw to it }
    }
    fun allowedTypes(target: String, videoId: String): Set<DownloadMediaType> = synchronized(lock) {
        val key = "$target/types/$videoId"
        if (!prefs.contains(key)) return@synchronized DownloadMediaType.entries.toSet()
        val names = runCatching { prefs.getStringSet(key, emptySet()) }.getOrNull().orEmpty()
        names.mapNotNull { runCatching { DownloadMediaType.valueOf(it) }.getOrNull() }.toSet()
    }
    fun add(target: String, owner: String, ids: Set<String>,
        types: Map<String, Set<DownloadMediaType>> = emptyMap()) = synchronized(lock) {
        if (ids.isEmpty() || target == owner) return@synchronized
        val existing = owners(target).toMutableMap()
        val editor = prefs.edit()
        ids.forEach {
            if (existing.putIfAbsent(it, owner) == null) editor.putStringSet("$target/types/$it",
                types[it].orEmpty().ifEmpty { DownloadMediaType.entries.toSet() }.mapTo(hashSetOf()) { type -> type.name })
        }
        check(editor.putString(target, JSONObject(existing as Map<*, *>).toString()).commit())
    }
    fun remove(target: String, videoId: String, type: DownloadMediaType? = null): Boolean = synchronized(lock) {
        val existing = owners(target).toMutableMap()
        if (videoId !in existing) return@synchronized false
        val remaining = if (type == null) emptySet() else allowedTypes(target, videoId) - type
        val editor = prefs.edit()
        if (remaining.isEmpty()) {
            existing.remove(videoId)
            editor.remove("$target/types/$videoId")
        } else editor.putStringSet("$target/types/$videoId", remaining.mapTo(hashSetOf()) { it.name })
        check(editor.putString(target, JSONObject(existing as Map<*, *>).toString()).commit())
        true
    }
    fun clearProfile(target: String) = synchronized(lock) {
        val editor = prefs.edit().remove(target)
        prefs.all.keys.filter { it.startsWith("$target/types/") }.forEach(editor::remove)
        check(editor.commit())
        cached.remove(target)
    }
    companion object { private val lock = Any() }
}
