package com.futo.platformplayer.compose.downloads

import android.content.Context
import com.futo.platformplayer.compose.ui.DownloadMediaType
import org.json.JSONArray
import org.json.JSONObject

/** Persistent equivalent of Grayjay's PlaylistDownloadDescriptor store. */
internal data class OfflinePlaylistDownload(
    val profileId: String,
    val playlistId: String,
    val mediaType: DownloadMediaType,
    val managedVideoIds: Set<String>,
    val excludedVideoIds: Set<String> = emptySet(),
    val targetVideoHeight: Int? = null,
)

internal class GrayjoyOfflinePlaylistStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val records = load().associateByTo(linkedMapOf()) { it.storageKey() }

    @Synchronized
    fun all(profileId: String): List<OfflinePlaylistDownload> = records.values
        .filter { it.profileId == profileId }

    /** An explicit "download playlist" action re-enables every current member. */
    @Synchronized
    fun register(
        profileId: String,
        playlistId: String,
        mediaType: DownloadMediaType,
        videoIds: Collection<String>,
        targetVideoHeight: Int? = null,
    ): OfflinePlaylistDownload {
        val currentIds = videoIds.filter(String::isNotBlank).toSet()
        val key = key(profileId, playlistId, mediaType)
        val existing = records[key]
        val updated = OfflinePlaylistDownload(
            profileId = profileId,
            playlistId = playlistId,
            mediaType = mediaType,
            managedVideoIds = currentIds,
            excludedVideoIds = existing?.excludedVideoIds.orEmpty() - currentIds,
            targetVideoHeight = targetVideoHeight?.takeIf { it > 0 },
        )
        records[key] = updated
        save()
        return updated
    }

    @Synchronized
    fun update(record: OfflinePlaylistDownload) {
        records[record.storageKey()] = record
        save()
    }

    @Synchronized
    fun remove(record: OfflinePlaylistDownload) {
        records.remove(record.storageKey())
        save()
    }

    @Synchronized
    fun remove(
        profileId: String,
        playlistId: String,
        mediaType: DownloadMediaType,
    ): OfflinePlaylistDownload? {
        val removed = records.remove(key(profileId, playlistId, mediaType)) ?: return null
        save()
        return removed
    }

    /** Manual cancellation mirrors Grayjay's preventDownload list. */
    @Synchronized
    fun excludeVideo(profileId: String, videoId: String, mediaType: DownloadMediaType) {
        var changed = false
        records.replaceAll { _, record ->
            if (
                record.profileId == profileId &&
                record.mediaType == mediaType &&
                videoId in record.managedVideoIds
            ) {
                changed = true
                record.copy(excludedVideoIds = record.excludedVideoIds + videoId)
            } else {
                record
            }
        }
        if (changed) save()
    }

    private fun load(): List<OfflinePlaylistDownload> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECORDS, "[]"))
        buildList {
            repeat(array.length()) { index ->
                val json = array.getJSONObject(index)
                add(
                    OfflinePlaylistDownload(
                        profileId = json.getString("profileId"),
                        playlistId = json.getString("playlistId"),
                        mediaType = DownloadMediaType.valueOf(json.getString("mediaType")),
                        managedVideoIds = json.getJSONArray("managedVideoIds").toStringSet(),
                        excludedVideoIds = json.optJSONArray("excludedVideoIds").toStringSet(),
                        targetVideoHeight = json.optInt("targetVideoHeight").takeIf { it > 0 },
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray().apply {
            records.values.forEach { record ->
                put(
                    JSONObject().apply {
                        put("profileId", record.profileId)
                        put("playlistId", record.playlistId)
                        put("mediaType", record.mediaType.name)
                        put("managedVideoIds", JSONArray(record.managedVideoIds.toList()))
                        put("excludedVideoIds", JSONArray(record.excludedVideoIds.toList()))
                        record.targetVideoHeight?.let { put("targetVideoHeight", it) }
                    },
                )
            }
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun OfflinePlaylistDownload.storageKey(): String = key(profileId, playlistId, mediaType)

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet == null) return@buildSet
        repeat(length()) { optString(it).takeIf(String::isNotBlank)?.let(::add) }
    }

    private companion object {
        const val PREFERENCES = "grayjoy_offline_playlists_v1"
        const val KEY_RECORDS = "records"

        fun key(profileId: String, playlistId: String, mediaType: DownloadMediaType): String =
            "$profileId\u0000$playlistId\u0000${mediaType.name}"
    }
}
