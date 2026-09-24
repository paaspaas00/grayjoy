package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.SubtitleUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class LibraryVideoState(
    val isWatchLater: Boolean,
    val isDownloaded: Boolean,
    val watchProgress: Float,
    val isLiked: Boolean = false,
    val lastWatchedAt: Long = 0L,
    val playlistNames: List<String> = emptyList(),
)

data class LibraryImportSnapshot(
    val videos: List<VideoUiModel>,
    val playlists: List<PlaylistUiModel>,
)

internal fun normalizedLibraryProgress(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun normalizePlaylistOrder(
    existingVideoIds: List<String>,
    requestedOrder: List<String>,
): List<String> {
    val existingIds = existingVideoIds.toSet()
    val requestedIds = requestedOrder.toSet()
    return requestedOrder.filter(existingIds::contains).distinct() +
        existingVideoIds.filterNot(requestedIds::contains)
}

internal fun normalizedPlaylistTitle(title: String): String = title
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)

internal fun playlistTitleExists(title: String, existingTitles: Collection<String>): Boolean {
    val normalized = normalizedPlaylistTitle(title)
    return normalized.isNotBlank() && existingTitles.any {
        normalizedPlaylistTitle(it) == normalized
    }
}

internal fun uniqueRemotePlaylistTitle(
    requestedTitle: String,
    channelName: String,
    existingTitles: Collection<String>,
    fallbackTitle: String,
): String {
    val baseTitle = requestedTitle.trim().ifBlank { fallbackTitle }.take(80)
    if (!playlistTitleExists(baseTitle, existingTitles)) return baseTitle

    val owner = channelName.trim().ifBlank { fallbackTitle }.take(32)
    val attributedBase = buildString {
        append(baseTitle.take((80 - owner.length - 3).coerceAtLeast(1)).trimEnd())
        append(" - ")
        append(owner)
    }.take(80)
    if (!playlistTitleExists(attributedBase, existingTitles)) return attributedBase

    var suffixNumber = 2
    while (true) {
        val suffix = " ($suffixNumber)"
        val candidate = attributedBase.take(80 - suffix.length).trimEnd() + suffix
        if (!playlistTitleExists(candidate, existingTitles)) return candidate
        suffixNumber += 1
    }
}

interface LibraryRepository {
    /** Shared by every repository and account-import transaction for the same profile. */
    val transactionLock: Any get() = this
    fun load(videos: List<VideoUiModel>): Map<String, LibraryVideoState>
    fun loadSavedVideos(): List<VideoUiModel>
    fun loadPlaylists(): List<PlaylistUiModel>
    fun saveVideo(video: VideoUiModel)
    fun saveVideos(videos: Collection<VideoUiModel>)
    fun saveDownloadDescriptor(video: VideoUiModel)
    fun clearDownloadDescriptor(videoId: String)
    fun reconcileDownloads(completedIds: Set<String>): Boolean {
        var changed = false
        loadSavedVideos().forEach { video ->
            val complete = video.id in completedIds
            if (video.isDownloaded != complete) { setDownloaded(video.id, complete); changed = true }
            if (!complete && (video.playbackUrl.isNotBlank() || video.playbackManifest.isNotBlank())) {
                clearDownloadDescriptor(video.id)
                changed = true
            }
        }
        return changed
    }
    fun recordHistory(video: VideoUiModel, progress: Float = video.watchProgress)
    fun setWatchLater(videoId: String, enabled: Boolean)
    fun setDownloaded(videoId: String, enabled: Boolean)
    fun setLiked(videoId: String, enabled: Boolean)
    fun setAvailable(videoId: String, available: Boolean)
    fun setScheduledStart(videoId: String, scheduledStartAtMs: Long)
    fun setWatchProgress(videoId: String, progress: Float)
    fun removeFromHistory(videoIds: Collection<String>)
    fun createPlaylist(title: String, videos: List<VideoUiModel>): PlaylistUiModel?
    fun renamePlaylist(playlistId: String, title: String): PlaylistUiModel?
    fun removePlaylists(playlistIds: Collection<String>): Int
    fun addVideosToPlaylist(playlistId: String, videos: List<VideoUiModel>): PlaylistUiModel?
    fun removeVideosFromPlaylist(playlistId: String, videoIds: Collection<String>): PlaylistUiModel?
    fun reorderPlaylist(playlistId: String, orderedVideoIds: List<String>): PlaylistUiModel?
    fun mergeImportedData(
        videos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
        repairSyntheticHistoryDates: Boolean = false,
    )
    fun createImportSnapshot(): LibraryImportSnapshot = LibraryImportSnapshot(
        videos = loadSavedVideos(),
        playlists = loadPlaylists(),
    )
    fun restoreImportSnapshot(snapshot: LibraryImportSnapshot)
}

internal class SharedPreferencesLibraryRepository(
    context: Context,
    profileId: String = "main",
) : LibraryRepository {
    private val appContext = context.applicationContext
    override val transactionLock: Any = profileDataLock(context, profileId)
    private val preferences = context.getSharedPreferences(
        if (profileId == "main") FILE_NAME else "${FILE_NAME}_$profileId",
        Context.MODE_PRIVATE,
    )
    private val watchProgressPreferences = context.getSharedPreferences(
        if (profileId == "main") PROGRESS_FILE_NAME else "${PROGRESS_FILE_NAME}_$profileId",
        Context.MODE_PRIVATE,
    )
    // Imported libraries are frequently several megabytes. Keep the parsed object graph for the
    // lifetime of this profile repository instead of reparsing the same JSON for every history,
    // playlist, download, and progress operation.
    private var cachedVideos: List<VideoUiModel>? = null
    private var cachedPlaylists: List<PlaylistUiModel>? = null
    private var cachedVideosJson: String? = null
    private var cachedPlaylistsJson: String? = null
    private var cachedProgressRevision = Long.MIN_VALUE
    internal val importJournal = AccountImportJournal(context, profileId)
    private var importRecoveryChecked = false

    private fun recoverInterruptedImport() {
        if (importRecoveryChecked) return
        val recovered = runCatching { importJournal.recoverIfNeeded() }
            .onFailure {
                // Reads remain available even if the filesystem also refuses quarantine. Every
                // mutation checks the journal separately and then fails closed until safe.
                android.util.Log.e("LibraryRepository", "Interrupted import recovery failed", it)
            }.getOrDefault(false)
        if (recovered) {
            cachedVideos = null
            cachedPlaylists = null
        }
        importRecoveryChecked = true
    }

    override fun load(videos: List<VideoUiModel>): Map<String, LibraryVideoState> {
        val saved = loadSavedVideos().associateBy(VideoUiModel::id)
        return videos.associate { video ->
            val stored = saved[video.id]
            video.id to LibraryVideoState(
                isWatchLater = stored?.isWatchLater ?: video.isWatchLater,
                isDownloaded = stored?.isDownloaded ?: video.isDownloaded,
                watchProgress = stored?.watchProgress ?: video.watchProgress,
                isLiked = stored?.isLiked ?: video.isLiked,
                lastWatchedAt = stored?.lastWatchedAt ?: video.lastWatchedAt,
                playlistNames = stored?.playlistNames ?: video.playlistNames,
            )
        }
    }

    override fun loadSavedVideos(): List<VideoUiModel> = synchronized(transactionLock) {
        readVideos().sortedWith(compareByDescending<VideoUiModel> { it.lastWatchedAt }.thenBy(VideoUiModel::title))
    }

    override fun loadPlaylists(): List<PlaylistUiModel> = synchronized(transactionLock) { readPlaylists() }

    override fun createImportSnapshot(): LibraryImportSnapshot = synchronized(transactionLock) {
        importJournal.ensureWritable()
        LibraryImportSnapshot(loadSavedVideos(), loadPlaylists())
    }

    override fun saveVideo(video: VideoUiModel) = saveVideos(listOf(video))

    override fun saveVideos(videos: Collection<VideoUiModel>) {
        return synchronized(transactionLock) {
            if (videos.isEmpty()) return
            importJournal.ensureWritable()
            val savedVideos = readVideos().associateByTo(linkedMapOf(), VideoUiModel::id)
            mergeSavedVideos(savedVideos, videos)
            writeVideos(savedVideos.values.toList())
        }
    }

    override fun saveDownloadDescriptor(video: VideoUiModel) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val videos = readVideos().associateByTo(linkedMapOf(), VideoUiModel::id)
            val existing = videos[video.id]
            videos[video.id] = video.copy(
                isWatchLater = existing?.isWatchLater ?: video.isWatchLater,
                isDownloaded = existing?.isDownloaded ?: false,
                isLiked = existing?.isLiked ?: video.isLiked,
                watchProgress = existing?.watchProgress ?: video.watchProgress,
                lastWatchedAt = existing?.lastWatchedAt ?: video.lastWatchedAt,
                playlistNames = existing?.playlistNames ?: video.playlistNames,
            ).forLocalStorage(preservePlayback = true)
            writeVideos(videos.values.toList())
        }
    }

    override fun clearDownloadDescriptor(videoId: String) {
        return synchronized(transactionLock) {
            updateVideo(videoId) {
                it.copy(
                    playbackUrl = "",
                    playbackCacheNamespace = "",
                    audioCacheNamespace = "",
                    playbackStreamKeys = emptyList(),
                    audioStreamKeys = emptyList(),
                    playbackMimeType = "",
                    playbackManifest = "",
                    audioUrl = "",
                    audioRequestHeaders = emptyMap(),
                    audioDataSourceFactory = null,
                    audioDownloadUrl = "",
                    audioDownloadMimeType = "",
                    audioDownloadManifest = "",
                    audioDownloadRequestHeaders = emptyMap(),
                    audioDownloadDataSourceFactory = null,
                    playbackRequestHeaders = emptyMap(),
                    playbackDataSourceFactory = null,
                    subtitleTracks = emptyList(),
                    qualityVariants = emptyList(),
                    audioQualityVariants = emptyList(),
                )
            }
        }
    }

    override fun reconcileDownloads(completedIds: Set<String>): Boolean {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val videos = readVideos()
            val updated = videos.map { reconcileVideoDownloadState(it, completedIds) }
            if (updated == videos) return false
            // One library serialization even if hundreds of downloads completed while we were away.
            writeVideos(updated)
            return true
        }
    }

    override fun recordHistory(video: VideoUiModel, progress: Float) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val normalizedProgress = normalizedLibraryProgress(progress)
            val videos = readVideos().associateByTo(linkedMapOf(), VideoUiModel::id)
            val existing = videos[video.id]
            videos[video.id] = video.copy(
                watchProgress = normalizedProgress,
                isWatchLater = existing?.isWatchLater ?: video.isWatchLater,
                isDownloaded = existing?.isDownloaded ?: video.isDownloaded,
                isLiked = existing?.isLiked ?: video.isLiked,
                playlistNames = existing?.playlistNames ?: video.playlistNames,
                lastWatchedAt = System.currentTimeMillis(),
            ).preservingStoredPlayback(existing)
            writeVideos(videos.values.toList())
            watchProgressPreferences.edit().putFloat(video.id, normalizedProgress)
                .putLong(KEY_PROGRESS_REVISION, nextProgressRevision()).apply()
            cachedProgressRevision = watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0L)
        }
    }

    override fun setWatchLater(videoId: String, enabled: Boolean) =
        updateVideo(videoId) { it.copy(isWatchLater = enabled) }

    override fun setDownloaded(videoId: String, enabled: Boolean) =
        updateVideo(videoId) { it.copy(isDownloaded = enabled) }

    override fun setLiked(videoId: String, enabled: Boolean) =
        updateVideo(videoId) { it.copy(isLiked = enabled) }

    override fun setAvailable(videoId: String, available: Boolean) =
        updateVideo(videoId) { it.copy(isAvailable = available) }

    override fun setScheduledStart(videoId: String, scheduledStartAtMs: Long) =
        updateVideo(videoId) { it.copy(scheduledStartAtMs = scheduledStartAtMs.coerceAtLeast(0L)) }

    override fun setWatchProgress(videoId: String, progress: Float) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            // The history itself is one large JSON value. Rewriting it every five seconds allocated
            // roughly 70 MB between GCs on a 200-item imported history and caused the visible periodic
            // playback hitch. Keep the frequently changing scalar in its own tiny preferences file;
            // readVideos() overlays it on the imported/stored JSON value for transparent migration.
            val normalizedProgress = normalizedLibraryProgress(progress)
            cachedVideos = readVideos().map { video ->
                if (video.id == videoId) video.copy(watchProgress = normalizedProgress) else video
            }
            watchProgressPreferences.edit()
                .putFloat(videoId, normalizedProgress)
                .putLong(KEY_PROGRESS_REVISION, nextProgressRevision())
                .apply()
            cachedProgressRevision = watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0)
        }
    }

    override fun removeFromHistory(videoIds: Collection<String>) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val removedIds = videoIds.toSet()
            if (removedIds.isEmpty()) return
            val videos = readVideos().mapNotNull { video ->
                if (video.id !in removedIds) return@mapNotNull video
                val withoutHistory = video.copy(watchProgress = 0f, lastWatchedAt = 0L)
                withoutHistory.takeIf {
                    it.isWatchLater || it.isDownloaded || it.isLiked || it.playlistNames.isNotEmpty()
                }
            }
            writeVideos(videos)
            val retainedIds = videos.mapTo(mutableSetOf(), VideoUiModel::id)
            watchProgressPreferences.edit().apply {
                putLong(KEY_PROGRESS_REVISION, nextProgressRevision())
                removedIds.forEach { videoId ->
                    if (videoId in retainedIds) putFloat(videoId, 0f) else remove(videoId)
                }
            }.apply()
            cachedProgressRevision = watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0L)
        }
    }

    override fun createPlaylist(title: String, videos: List<VideoUiModel>): PlaylistUiModel? {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val normalizedTitle = title.trim().ifBlank { appContext.getString(R.string.new_playlist) }
                .take(80)
            val playlists = readPlaylists()
            if (playlistTitleExists(normalizedTitle, playlists.map(PlaylistUiModel::title))) return null
            val playlist = PlaylistUiModel(
                id = UUID.randomUUID().toString(),
                title = normalizedTitle,
                description = appContext.getString(R.string.local_playlist_description),
                videoIds = videos.map(VideoUiModel::id).distinct(),
            )
            persistPlaylistMutation(playlists + playlist, videos)
            return playlist
        }
    }

    override fun renamePlaylist(playlistId: String, title: String): PlaylistUiModel? {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val playlists = readPlaylists()
            val existing = playlists.firstOrNull { it.id == playlistId } ?: return null
            val normalizedTitle = title.trim().take(80)
            if (normalizedTitle.isBlank()) return null
            if (
                playlistTitleExists(
                    normalizedTitle,
                    playlists.filterNot { it.id == playlistId }.map(PlaylistUiModel::title),
                )
            ) return null
            val renamed = existing.copy(title = normalizedTitle)
            persistPlaylistMutation(playlists.map { if (it.id == playlistId) renamed else it })
            return renamed
        }
    }

    override fun removePlaylists(playlistIds: Collection<String>): Int {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val removedIds = playlistIds.toSet()
            if (removedIds.isEmpty()) return 0
            val playlists = readPlaylists()
            val retained = playlists.filterNot { it.id in removedIds }
            val removedCount = playlists.size - retained.size
            if (removedCount == 0) return 0
            persistPlaylistMutation(retained)
            return removedCount
        }
    }

    override fun addVideosToPlaylist(
        playlistId: String,
        videos: List<VideoUiModel>,
    ): PlaylistUiModel? {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val playlists = readPlaylists()
            val existing = playlists.firstOrNull { it.id == playlistId } ?: return null
            val updated = existing.copy(
                videoIds = (existing.videoIds + videos.map(VideoUiModel::id)).distinct(),
            )
            persistPlaylistMutation(
                playlists = playlists.map { if (it.id == playlistId) updated else it },
                incomingVideos = videos,
            )
            return updated
        }
    }

    override fun removeVideosFromPlaylist(
        playlistId: String,
        videoIds: Collection<String>,
    ): PlaylistUiModel? {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val playlists = readPlaylists()
            val existing = playlists.firstOrNull { it.id == playlistId } ?: return null
            val removedIds = videoIds.toSet()
            val updated = existing.copy(videoIds = existing.videoIds.filterNot(removedIds::contains))
            persistPlaylistMutation(playlists.map { if (it.id == playlistId) updated else it })
            return updated
        }
    }

    override fun reorderPlaylist(
        playlistId: String,
        orderedVideoIds: List<String>,
    ): PlaylistUiModel? {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val playlists = readPlaylists()
            val existing = playlists.firstOrNull { it.id == playlistId } ?: return null
            val normalizedOrder = normalizePlaylistOrder(existing.videoIds, orderedVideoIds)
            val updated = existing.copy(videoIds = normalizedOrder)
            persistPlaylistMutation(playlists.map { if (it.id == playlistId) updated else it })
            return updated
        }
    }

    override fun mergeImportedData(
        videos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
        repairSyntheticHistoryDates: Boolean,
    ) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val mergedVideos = readVideos().associateByTo(linkedMapOf(), VideoUiModel::id)
            val syntheticHistoryIds = if (repairSyntheticHistoryDates) {
                syntheticHistoryFallbackIds(mergedVideos.values)
            } else {
                emptySet()
            }
            videos.forEach { imported ->
                val existing = mergedVideos[imported.id]
                mergedVideos[imported.id] = if (existing == null) {
                    imported.forLocalStorage()
                } else {
                    existing.mergeImported(
                        imported = imported,
                        preferImportedHistory = imported.id in syntheticHistoryIds,
                    ).forLocalStorage()
                }
            }
            writeVideos(mergedVideos.values.toList())
            watchProgressPreferences.edit().apply {
                putLong(KEY_PROGRESS_REVISION, nextProgressRevision())
                videos.forEach { imported ->
                    mergedVideos[imported.id]?.let { merged ->
                        putFloat(imported.id, normalizedLibraryProgress(merged.watchProgress))
                    }
                }
            }.apply()
            cachedProgressRevision = watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0L)

            val mergedPlaylists = readPlaylists().toMutableList()
            playlists.forEach { imported ->
                val index = mergedPlaylists.indexOfFirst {
                    it.id == imported.id || it.title.equals(imported.title, ignoreCase = true)
                }
                if (index < 0) {
                    mergedPlaylists += imported
                } else {
                    val existing = mergedPlaylists[index]
                    mergedPlaylists[index] = existing.copy(
                        videoIds = (existing.videoIds + imported.videoIds).distinct(),
                    )
                }
            }
            persistPlaylistMutation(mergedPlaylists)
        }
    }

    override fun restoreImportSnapshot(snapshot: LibraryImportSnapshot) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            cachedVideos = snapshot.videos
            cachedPlaylists = snapshot.playlists
            val videoJson = JSONArray().apply { snapshot.videos.forEach { put(it.toJson()) } }
            val playlistJson = JSONArray().apply { snapshot.playlists.forEach { put(it.toJson()) } }
            cachedVideosJson = videoJson.toString()
            cachedPlaylistsJson = playlistJson.toString()
            check(
                preferences.edit()
                    .putString(KEY_VIDEOS, cachedVideosJson)
                    .putString(KEY_PLAYLISTS, cachedPlaylistsJson)
                    .commit(),
            ) { "Could not restore the library import transaction." }
            check(
                watchProgressPreferences.edit().clear().apply {
                    putLong(KEY_PROGRESS_REVISION, nextProgressRevision())
                    snapshot.videos.forEach { video ->
                        putFloat(video.id, normalizedLibraryProgress(video.watchProgress))
                    }
                }.commit(),
            ) { "Could not restore watch progress after a cancelled import." }
            cachedProgressRevision = watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0L)
        }
    }

    private fun updateVideo(videoId: String, transform: (VideoUiModel) -> VideoUiModel) {
        return synchronized(transactionLock) {
            importJournal.ensureWritable()
            val videos = readVideos()
            val index = videos.indexOfFirst { it.id == videoId }
            if (index < 0) return
            val updated = transform(videos[index])
            if (updated == videos[index]) return
            writeVideos(videos.toMutableList().apply { this[index] = updated })
        }
    }

    private fun persistPlaylistMutation(
        playlists: List<PlaylistUiModel>,
        incomingVideos: Collection<VideoUiModel> = emptyList(),
    ) {
        writePlaylists(playlists)
        val playlistNamesByVideoId = buildMap<String, MutableList<String>> {
            playlists.forEach { playlist ->
                playlist.videoIds.distinct().forEach { videoId ->
                    getOrPut(videoId) { mutableListOf() }.add(playlist.title)
                }
            }
        }
        val savedVideos = readVideos().associateByTo(linkedMapOf(), VideoUiModel::id)
        mergeSavedVideos(savedVideos, incomingVideos)
        val videos = savedVideos.values.map { video ->
            video.copy(
                playlistNames = playlistNamesByVideoId[video.id].orEmpty(),
            )
        }
        writeVideos(videos)
    }

    private fun mergeSavedVideos(
        savedVideos: MutableMap<String, VideoUiModel>,
        incomingVideos: Collection<VideoUiModel>,
    ) {
        incomingVideos.forEach { video ->
            val existing = savedVideos[video.id]
            savedVideos[video.id] = video.copy(
                isWatchLater = existing?.isWatchLater ?: video.isWatchLater,
                isDownloaded = existing?.isDownloaded ?: video.isDownloaded,
                isLiked = existing?.isLiked ?: video.isLiked,
                watchProgress = existing?.watchProgress ?: video.watchProgress,
                lastWatchedAt = existing?.lastWatchedAt ?: video.lastWatchedAt,
                playlistNames = existing?.playlistNames ?: video.playlistNames,
            ).preservingStoredPlayback(existing)
        }
    }

    private fun readVideos(): List<VideoUiModel> {
        recoverInterruptedImport()
        val storedJson = preferences.getString(KEY_VIDEOS, null)
        val revision = watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0)
        if (cachedVideosJson === storedJson && cachedProgressRevision == revision) {
            cachedVideos?.let { return it }
        }
        val progressOverrides = watchProgressPreferences.all
        return runCatching {
            storedJson
            ?.let(::JSONArray)
            ?.toVideoList()
            .orEmpty()
            .map { video ->
                val persistedProgress = (progressOverrides[video.id] as? Number)?.toFloat()
                if (persistedProgress == null) video else {
                    video.copy(watchProgress = normalizedLibraryProgress(persistedProgress))
                }
            }
        }.getOrDefault(emptyList()).also {
            cachedVideos = it
            cachedVideosJson = storedJson
            cachedProgressRevision = revision
        }
    }

    private fun writeVideos(videos: List<VideoUiModel>) {
        importJournal.ensureWritable()
        cachedVideos = videos
        val json = JSONArray().apply { videos.forEach { put(it.toJson()) } }
        cachedVideosJson = json.toString()
        preferences.edit().putString(KEY_VIDEOS, cachedVideosJson).apply()
    }

    private fun readPlaylists(): List<PlaylistUiModel> {
        recoverInterruptedImport()
        val storedJson = preferences.getString(KEY_PLAYLISTS, null)
        if (cachedPlaylistsJson === storedJson) cachedPlaylists?.let { return it }
        return runCatching {
            storedJson
                ?.let(::JSONArray)
                ?.toPlaylistList(appContext.getString(R.string.local_playlist_description))
                .orEmpty()
        }.getOrDefault(emptyList()).also {
            cachedPlaylists = it
            cachedPlaylistsJson = storedJson
        }
    }

    private fun writePlaylists(playlists: List<PlaylistUiModel>) {
        importJournal.ensureWritable()
        cachedPlaylists = playlists
        val json = JSONArray().apply { playlists.forEach { put(it.toJson()) } }
        cachedPlaylistsJson = json.toString()
        preferences.edit().putString(KEY_PLAYLISTS, cachedPlaylistsJson).apply()
    }

    private fun nextProgressRevision(): Long =
        watchProgressPreferences.getLong(KEY_PROGRESS_REVISION, 0L) + 1L

    private companion object {
        const val FILE_NAME = "grayjay_compose_library_v2"
        const val PROGRESS_FILE_NAME = "grayjay_compose_watch_progress_v1"
        const val KEY_VIDEOS = "saved_videos"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_PROGRESS_REVISION = "__progress_revision"
    }
}

internal fun List<VideoUiModel>.withLibraryState(
    libraryState: Map<String, LibraryVideoState>,
): List<VideoUiModel> = map { video ->
    libraryState[video.id]?.let { state ->
        video.copy(
            isWatchLater = state.isWatchLater,
            isDownloaded = state.isDownloaded,
            watchProgress = state.watchProgress,
            isLiked = state.isLiked,
            lastWatchedAt = state.lastWatchedAt,
            playlistNames = state.playlistNames,
        )
    } ?: video
}

internal fun reconcileVideoDownloadState(video: VideoUiModel, completedIds: Set<String>): VideoUiModel {
    val complete = video.id in completedIds
    val updated = if (video.isDownloaded == complete) video else video.copy(isDownloaded = complete)
    return if (!complete && (updated.playbackUrl.isNotBlank() || updated.playbackManifest.isNotBlank())) {
        updated.forLocalStorage()
    } else updated
}

internal fun VideoUiModel.forLocalStorage(preservePlayback: Boolean = false) = copy(
    watchProgress = normalizedLibraryProgress(watchProgress),
    playbackFromDownload = false,
    playbackCacheNamespace = "",
    audioCacheNamespace = "",
    playbackStreamKeys = emptyList(),
    audioStreamKeys = emptyList(),
    playbackUrl = playbackUrl.takeIf { preservePlayback }.orEmpty(),
    playbackMimeType = playbackMimeType.takeIf { preservePlayback }.orEmpty(),
    playbackManifest = playbackManifest.takeIf { preservePlayback }.orEmpty(),
    audioUrl = audioUrl.takeIf { preservePlayback }.orEmpty(),
    audioRequestHeaders = audioRequestHeaders.takeIf { preservePlayback }.orEmpty(),
    audioDataSourceFactory = null,
    audioDownloadUrl = "",
    audioDownloadMimeType = "",
    audioDownloadManifest = "",
    audioDownloadRequestHeaders = emptyMap(),
    audioDownloadDataSourceFactory = null,
    playbackRequestHeaders = playbackRequestHeaders.takeIf { preservePlayback }.orEmpty(),
    playbackDataSourceFactory = null,
    drmLicenseUri = "",
    drmLicenseRequestExecutor = null,
    playbackTracker = null,
    subtitleTracks = subtitleTracks.takeIf { preservePlayback }.orEmpty(),
    qualityVariants = emptyList(),
    audioQualityVariants = emptyList(),
)

private fun VideoUiModel.preservingStoredPlayback(existing: VideoUiModel?): VideoUiModel {
    val stored = existing?.takeIf { it.playbackUrl.isNotBlank() || it.playbackManifest.isNotBlank() }
        ?: return forLocalStorage()
    return copy(
        playbackUrl = stored.playbackUrl,
        playbackMimeType = stored.playbackMimeType,
        playbackManifest = stored.playbackManifest,
        audioUrl = stored.audioUrl,
        audioRequestHeaders = stored.audioRequestHeaders,
        audioDataSourceFactory = null,
        playbackRequestHeaders = stored.playbackRequestHeaders,
        playbackDataSourceFactory = null,
        subtitleTracks = stored.subtitleTracks,
        qualityVariants = emptyList(),
        audioQualityVariants = emptyList(),
    ).forLocalStorage(preservePlayback = true)
}

internal fun syntheticHistoryFallbackIds(videos: Collection<VideoUiModel>): Set<String> {
    val ordered = videos
        .filter { it.lastWatchedAt > 0L }
        .sortedByDescending(VideoUiModel::lastWatchedAt)
    if (ordered.size < 3) return emptySet()

    val syntheticIds = mutableSetOf<String>()
    var runStart = 0
    for (index in 1..ordered.size) {
        val continuesRun = index < ordered.size &&
            ordered[index - 1].lastWatchedAt - ordered[index].lastWatchedAt == 1L
        if (continuesRun) continue
        if (index - runStart >= 3) {
            ordered.subList(runStart, index).mapTo(syntheticIds, VideoUiModel::id)
        }
        runStart = index
    }
    return syntheticIds
}

private fun VideoUiModel.mergeImported(
    imported: VideoUiModel,
    preferImportedHistory: Boolean = false,
): VideoUiModel {
    fun preferCurrent(current: String, fallback: String): String = when {
        current.isBlank() -> fallback
        current == id && fallback.isNotBlank() -> fallback
        current == "Unknown creator" && fallback.isNotBlank() -> fallback
        else -> current
    }
    val importedHistoryIsNewer = preferImportedHistory ||
        imported.lastWatchedAt > lastWatchedAt
    return copy(
        title = preferCurrent(title, imported.title),
        creator = preferCurrent(creator, imported.creator),
        metadata = preferCurrent(metadata, imported.metadata),
        duration = preferCurrent(duration, imported.duration),
        viewCount = maxOf(viewCount, imported.viewCount),
        publishedAtMs = maxOf(publishedAtMs, imported.publishedAtMs),
        channelId = preferCurrent(channelId, imported.channelId),
        sourceId = if (sourceId.isBlank()) imported.sourceId else sourceId,
        isLive = isLive || imported.isLive,
        isShort = isShort || imported.isShort,
        isAvailable = isAvailable && imported.isAvailable,
        scheduledStartAtMs = maxOf(scheduledStartAtMs, imported.scheduledStartAtMs),
        watchProgress = if (importedHistoryIsNewer) imported.watchProgress else watchProgress,
        isDownloaded = isDownloaded || imported.isDownloaded,
        isWatchLater = isWatchLater || imported.isWatchLater,
        isLiked = isLiked || imported.isLiked,
        lastWatchedAt = if (preferImportedHistory && imported.lastWatchedAt > 0L) {
            imported.lastWatchedAt
        } else {
            maxOf(lastWatchedAt, imported.lastWatchedAt)
        },
        contentUrl = preferCurrent(contentUrl, imported.contentUrl),
        thumbnailUrl = preferCurrent(thumbnailUrl, imported.thumbnailUrl),
        description = preferCurrent(description, imported.description),
        shareUrl = preferCurrent(shareUrl, imported.shareUrl),
        authorUrl = preferCurrent(authorUrl, imported.authorUrl),
        authorThumbnailUrl = preferCurrent(authorThumbnailUrl, imported.authorThumbnailUrl),
        authorSubscriberCount = authorSubscriberCount ?: imported.authorSubscriberCount,
        likeCount = likeCount ?: imported.likeCount,
        dislikeCount = dislikeCount ?: imported.dislikeCount,
        sourceName = preferCurrent(sourceName, imported.sourceName),
        sourceIconUrl = preferCurrent(sourceIconUrl, imported.sourceIconUrl),
    )
}

internal fun VideoUiModel.toJson() = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("creator", creator)
    put("metadata", metadata)
    put("duration", duration)
    put("viewCount", viewCount)
    put("publishedAtMs", publishedAtMs)
    put("channelId", channelId)
    put("sourceId", sourceId)
    put("isLive", isLive)
    put("isShort", isShort)
    put("isAvailable", isAvailable)
    put("scheduledStartAtMs", scheduledStartAtMs)
    put("watchProgress", normalizedLibraryProgress(watchProgress).toDouble())
    put("isDownloaded", isDownloaded)
    put("isWatchLater", isWatchLater)
    put("isLiked", isLiked)
    put("lastWatchedAt", lastWatchedAt)
    put("playlistNames", JSONArray(playlistNames))
    put("contentUrl", contentUrl)
    put("thumbnailUrl", thumbnailUrl)
    put("description", description)
    put("shareUrl", shareUrl)
    put("authorUrl", authorUrl)
    put("authorThumbnailUrl", authorThumbnailUrl)
    put("authorSubscriberCount", authorSubscriberCount ?: JSONObject.NULL)
    put("likeCount", likeCount ?: JSONObject.NULL)
    put("dislikeCount", dislikeCount ?: JSONObject.NULL)
    put("sourceName", sourceName)
    put("sourceIconUrl", sourceIconUrl)
    put("playbackUrl", playbackUrl)
    put("playbackMimeType", playbackMimeType)
    put("playbackManifest", playbackManifest)
    put("audioUrl", audioUrl)
    put("audioRequestHeaders", JSONObject(audioRequestHeaders))
    put("playbackRequestHeaders", JSONObject(playbackRequestHeaders))
    put(
        "subtitleTracks",
        JSONArray().apply {
            subtitleTracks.forEach { subtitle ->
                put(
                    JSONObject().apply {
                        put("name", subtitle.name)
                        put("language", subtitle.language ?: JSONObject.NULL)
                        put("uri", subtitle.uri)
                        put("mimeType", subtitle.mimeType)
                        put("requestHeaders", JSONObject(subtitle.requestHeaders))
                        put("cacheNamespace", subtitle.cacheNamespace)
                    },
                )
            }
        },
    )
}

internal fun JSONArray.toVideoList(): List<VideoUiModel> = buildList {
    val seenIds = hashSetOf<String>()
    for (index in 0 until length()) {
        val json = optJSONObject(index) ?: continue
        val id = json.optString("id")
        if (id.isBlank() || !seenIds.add(id)) continue
        add(
            VideoUiModel(
                id = id,
                title = json.optString("title"),
                creator = json.optString("creator"),
                metadata = json.optString("metadata"),
                duration = json.optString("duration"),
                viewCount = json.optLong("viewCount"),
                publishedAtMs = json.optLong("publishedAtMs"),
                channelId = json.optString("channelId"),
                sourceId = json.optString("sourceId", "youtube"),
                isLive = json.optBoolean("isLive"),
                isShort = json.optBoolean("isShort") ||
                    id.contains("/shorts/", ignoreCase = true),
                isAvailable = json.optBoolean("isAvailable", true),
                scheduledStartAtMs = json.optLong("scheduledStartAtMs", 0L),
                watchProgress = normalizedLibraryProgress(json.optDouble("watchProgress", 0.0).toFloat()),
                isDownloaded = json.optBoolean("isDownloaded"),
                isWatchLater = json.optBoolean("isWatchLater"),
                isLiked = json.optBoolean("isLiked"),
                lastWatchedAt = json.optLong("lastWatchedAt"),
                playlistNames = json.optJSONArray("playlistNames").toStringList(),
                contentUrl = json.optString("contentUrl"),
                thumbnailUrl = json.optString("thumbnailUrl"),
                description = json.optString("description"),
                shareUrl = json.optString("shareUrl"),
                authorUrl = json.optString("authorUrl"),
                authorThumbnailUrl = json.optString("authorThumbnailUrl"),
                authorSubscriberCount = json.optNullableLong("authorSubscriberCount"),
                likeCount = json.optNullableLong("likeCount"),
                dislikeCount = json.optNullableLong("dislikeCount"),
                sourceName = json.optString("sourceName"),
                sourceIconUrl = json.optString("sourceIconUrl"),
                playbackUrl = json.optString("playbackUrl"),
                playbackMimeType = json.optString("playbackMimeType"),
                playbackManifest = json.optString("playbackManifest"),
                audioUrl = json.optString("audioUrl"),
                audioRequestHeaders = json.optJSONObject("audioRequestHeaders").toStringMap(),
                playbackRequestHeaders = json.optJSONObject("playbackRequestHeaders").toStringMap(),
                subtitleTracks = json.optJSONArray("subtitleTracks").toSubtitleTracks(),
            ),
        )
    }
}

private fun PlaylistUiModel.toJson() = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("description", description)
    put("videoIds", JSONArray(videoIds))
}

private fun JSONArray.toPlaylistList(defaultDescription: String): List<PlaylistUiModel> = buildList {
    val seenIds = hashSetOf<String>()
    for (index in 0 until length()) {
        val json = optJSONObject(index) ?: continue
        val id = json.optString("id")
        if (id.isBlank() || !seenIds.add(id)) continue
        add(
            PlaylistUiModel(
                id = id,
                title = json.optString("title"),
                description = json.optString(
                    "description",
                    defaultDescription,
                ),
                videoIds = json.optJSONArray("videoIds").toStringList(),
            ),
        )
    }
}

private fun JSONArray?.toStringList(): List<String> = if (this == null) {
    emptyList()
} else {
    buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> = if (this == null) {
    emptyMap()
} else {
    keys().asSequence().mapNotNull { key ->
        optString(key).takeIf(String::isNotBlank)?.let { value -> key to value }
    }.toMap()
}

private fun JSONArray?.toSubtitleTracks(): List<SubtitleUiModel> = if (this == null) {
    emptyList()
} else {
    buildList {
        for (index in 0 until length()) {
            val json = optJSONObject(index) ?: continue
            val uri = json.optString("uri")
            if (uri.isBlank()) continue
            add(
                SubtitleUiModel(
                    name = json.optString("name"),
                    language = json.optString("language").takeIf(String::isNotBlank),
                    uri = uri,
                    mimeType = json.optString("mimeType"),
                    requestHeaders = json.optJSONObject("requestHeaders").toStringMap(),
                    cacheNamespace = json.optString("cacheNamespace"),
                ),
            )
        }
    }
}

private fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)
