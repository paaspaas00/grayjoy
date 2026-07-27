package com.futo.platformplayer.compose.downloads

import android.content.Context
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import org.json.JSONArray
import org.json.JSONObject

internal data class QueuedDownload(
    val profileId: String,
    val videoId: String,
    val mediaType: DownloadMediaType,
    val status: DownloadStatus,
    val createdAtMs: Long,
    val targetVideoHeight: Int? = null,
    val targetAudioBitrate: Int? = null,
    val errorMessage: String? = null,
)

/**
 * Persistent equivalent of Grayjay's `_downloading` store for jobs that have not yet been
 * handed to Media3. Media3 persists transfers after preparation; this store preserves the
 * equally important queued/preparing/error portion of the lifecycle.
 */
internal class GrayjoyDownloadQueue(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val records = load().associateByTo(linkedMapOf()) { record ->
        key(record.profileId, record.videoId, record.mediaType)
    }

    @Synchronized
    fun all(profileId: String): List<QueuedDownload> = records.values
        .filter { it.profileId == profileId }
        .sortedBy(QueuedDownload::createdAtMs)

    @Synchronized
    fun put(record: QueuedDownload) = putAll(listOf(record))

    @Synchronized
    fun putAll(newRecords: Collection<QueuedDownload>) {
        if (newRecords.isEmpty()) return
        newRecords.forEach { record -> records[record.key()] = record }
        save()
    }

    @Synchronized
    fun remove(profileId: String, videoId: String, mediaType: DownloadMediaType) {
        if (records.remove(key(profileId, videoId, mediaType)) != null) save()
    }

    @Synchronized
    fun removeVideo(profileId: String, videoId: String) {
        val keys = records.filterValues { record ->
            record.profileId == profileId && record.videoId == videoId
        }.keys
        if (keys.isEmpty()) return
        keys.forEach(records::remove)
        save()
    }

    @Synchronized
    fun get(
        profileId: String,
        videoId: String,
        mediaType: DownloadMediaType,
    ): QueuedDownload? = records[key(profileId, videoId, mediaType)]

    private fun load(): List<QueuedDownload> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val json = array.getJSONObject(index)
                    val status = runCatching {
                        DownloadStatus.valueOf(json.getString("status"))
                    }.getOrDefault(DownloadStatus.Queued)
                    add(
                        QueuedDownload(
                            profileId = json.getString("profileId"),
                            videoId = json.getString("videoId"),
                            mediaType = DownloadMediaType.valueOf(json.getString("mediaType")),
                            // A process cannot resume inside plugin preparation; repeat it.
                            status = if (status == DownloadStatus.Preparing) {
                                DownloadStatus.Queued
                            } else status,
                            createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis()),
                            targetVideoHeight = json.optInt("targetVideoHeight")
                                .takeIf { it > 0 },
                            targetAudioBitrate = json.optInt("targetAudioBitrate")
                                .takeIf { it > 0 },
                            errorMessage = json.optString("errorMessage").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val array = JSONArray().apply {
            records.values.forEach { record ->
                put(
                    JSONObject().apply {
                        put("profileId", record.profileId)
                        put("videoId", record.videoId)
                        put("mediaType", record.mediaType.name)
                        put("status", record.status.name)
                        put("createdAtMs", record.createdAtMs)
                        record.targetVideoHeight?.let { put("targetVideoHeight", it) }
                        record.targetAudioBitrate?.let { put("targetAudioBitrate", it) }
                        record.errorMessage?.let { put("errorMessage", it) }
                    },
                )
            }
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun QueuedDownload.key(): String = key(profileId, videoId, mediaType)

    private companion object {
        const val PREFERENCES_NAME = "grayjoy_download_queue_v1"
        const val KEY_RECORDS = "records"

        fun key(profileId: String, videoId: String, mediaType: DownloadMediaType): String =
            "$profileId\u0000$videoId\u0000${mediaType.name}"
    }
}
