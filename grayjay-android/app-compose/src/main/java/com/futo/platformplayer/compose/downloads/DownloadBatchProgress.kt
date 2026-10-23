package com.futo.platformplayer.compose.downloads

import android.content.Context
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel

data class DownloadBatchProgress(val total: Int = 0, val completed: Int = 0, val fraction: Float = 0f)

internal class DownloadBatchTracker(initial: Set<String> = emptySet()) {
    private val participants = initial.toMutableSet()
    private var wasIdle = false
    fun keys(): Set<String> = participants.toSet()
    fun update(downloads: Collection<DownloadUiModel>): DownloadBatchProgress {
        val active = downloads.filter { it.isActive && it.status != DownloadStatus.Removing }
        if (wasIdle && active.isNotEmpty()) participants.clear()
        active.forEach { item ->
            val types = item.activeMediaTypes.ifEmpty { setOf(item.mediaType) }
            types.forEach { participants += key(item.videoId, it) }
        }
        val byId = downloads.associateBy(DownloadUiModel::videoId)
        // A cancelled/deleted member is no longer work to do. Successful members stay in the denominator.
        participants.removeAll { value ->
            val id = value.substringBeforeLast('\u0000')
            val type = runCatching { DownloadMediaType.valueOf(value.substringAfterLast('\u0000')) }.getOrNull()
            val item = byId[id]
            item == null || type == null || item.status == DownloadStatus.Removing ||
                (!item.isComplete(type) && !item.isActive(type) && type !in item.failedMediaTypes && item.status != DownloadStatus.Failed)
        }
        var completed = 0
        val sum = participants.sumOf { value ->
            val item = byId.getValue(value.substringBeforeLast('\u0000'))
            val type = DownloadMediaType.valueOf(value.substringAfterLast('\u0000'))
            if (item.isComplete(type)) { completed++; 1.0 }
            else item.progress?.takeIf(Float::isFinite)?.coerceIn(0f, 1f)?.toDouble() ?: 0.0
        }
        wasIdle = active.isEmpty()
        return DownloadBatchProgress(participants.size, completed,
            if (participants.isEmpty()) 0f else (sum / participants.size).toFloat())
    }
    private fun key(id: String, type: DownloadMediaType) = "$id\u0000${type.name}"
}

/** Membership is persisted only when it changes, not on every byte/progress callback. */
internal class DownloadBatchProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("grayjoy_download_batch_progress", Context.MODE_PRIVATE)
    private val trackers = mutableMapOf<String, DownloadBatchTracker>()
    fun update(profile: String, downloads: Collection<DownloadUiModel>, queue: List<QueuedDownload>): DownloadBatchProgress {
        val tracker = trackers.getOrPut(profile) {
            DownloadBatchTracker(preferences.getStringSet(profile, emptySet()).orEmpty())
        }
        val before = tracker.keys()
        // The transfer manager may initialize before pre-transfer work has been restored to
        // the UI. Include the durable queue so that interval cannot erase batch membership.
        val known = downloads.associateByTo(mutableMapOf(), DownloadUiModel::videoId)
        queue.forEach { pending ->
            val current = known[pending.videoId]
            if (current?.isComplete(pending.mediaType) != true && current?.isActive(pending.mediaType) != true) {
                known[pending.videoId] = DownloadUiModel(profile, pending.videoId, pending.mediaType, pending.status,
                    completedMediaTypes = current?.completedMediaTypes.orEmpty(),
                    activeMediaTypes = current?.activeMediaTypes.orEmpty() + pending.mediaType)
            }
        }
        val result = tracker.update(known.values)
        val after = tracker.keys()
        if (after != before) preferences.edit().putStringSet(profile, after).apply()
        return result
    }
}
