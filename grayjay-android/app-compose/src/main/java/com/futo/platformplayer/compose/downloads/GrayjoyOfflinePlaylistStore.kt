package com.futo.platformplayer.compose.downloads

import android.content.Context
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import org.json.JSONArray
import org.json.JSONObject
import com.google.gson.JsonParser

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

    /** A background reconciliation must not overwrite a newer cancel/register action. */
    @Synchronized
    fun replaceProfileIfUnchanged(
        profileId: String,
        expected: List<OfflinePlaylistDownload>,
        updated: List<OfflinePlaylistDownload>,
    ): Boolean {
        require(expected.all { it.profileId == profileId } && updated.all { it.profileId == profileId })
        val before = expected.associateBy { it.storageKey() }
        if (records.filterValues { it.profileId == profileId } != before) return false
        val replacement = updated.associateBy { it.storageKey() }
        if (before == replacement) return true
        before.keys.forEach(records::remove)
        records.putAll(replacement)
        save()
        return true
    }

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

    private fun load(): List<OfflinePlaylistDownload> = parseOfflinePlaylistRecords(
        runCatching { preferences.getString(KEY_RECORDS, "[]") }.getOrNull().orEmpty(),
    )

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

/** Consult the latest descriptors after reconciliation and again before starting a transfer. */
internal fun automaticPlaylistDownloadCandidate(
    descriptors: List<OfflinePlaylistDownload>,
    profileId: String,
    videoId: String,
    mediaType: DownloadMediaType,
    download: DownloadUiModel?,
): OfflinePlaylistDownload? {
    val existing = download?.takeIf { it.profileId == profileId && it.videoId == videoId }
    if (existing?.isComplete(mediaType) == true || existing?.isActive(mediaType) == true) return null
    return descriptors.firstOrNull {
        it.profileId == profileId && it.mediaType == mediaType &&
            videoId in it.managedVideoIds && videoId !in it.excludedVideoIds
    }
}

internal fun offlinePlaylistHasOwner(
    descriptors: List<OfflinePlaylistDownload>,
    profileId: String,
    videoId: String,
    mediaType: DownloadMediaType,
): Boolean = descriptors.any {
    it.profileId == profileId && it.mediaType == mediaType && videoId in it.managedVideoIds
}

/** A damaged record must not disable automatic downloads for unrelated playlists/profiles. */
internal fun parseOfflinePlaylistRecords(raw: String): List<OfflinePlaylistDownload> {
    val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { element -> runCatching {
        if (!element.isJsonObject) return@runCatching null
        val json = element.asJsonObject
        fun id(name: String): String? = json.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() && '\u0000' !in it }
        fun ids(name: String): Set<String> = json.get(name)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }
                ?.asString?.takeIf(String::isNotBlank) }?.toSet().orEmpty()
        val profile = id("profileId") ?: return@runCatching null
        val playlist = id("playlistId") ?: return@runCatching null
        val type = id("mediaType")?.let(DownloadMediaType::valueOf) ?: return@runCatching null
        if (json.get("managedVideoIds")?.isJsonArray != true) return@runCatching null
        OfflinePlaylistDownload(profile, playlist, type, ids("managedVideoIds"), ids("excludedVideoIds"),
            runCatching { json.get("targetVideoHeight")?.asInt }.getOrNull()?.takeIf { it > 0 })
    }.getOrNull() }
}
