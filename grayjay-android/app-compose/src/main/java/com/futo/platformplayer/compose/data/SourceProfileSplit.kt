package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.engine.EngineUserImportResult
import com.futo.platformplayer.compose.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import com.futo.platformplayer.backend.GrayjayPluginAuthStore

internal data class SourceProfileCopy(val sourceProfileId: String, val targetProfileId: String, val videoIds: Set<String>)

internal fun profileCopyEligible(profile: ProfileUiModel): Boolean =
    profile.id != "private" && profile.protection == ProfileProtection.None && profile.sourceId.isBlank()

internal fun partitionSourceLibrary(snapshot: LibrarySnapshot, sourceId: String, origin: String,
    emptyPlaylistSources: Set<String> = emptySet()): LibraryImportSnapshot {
    val videos = snapshot.videos.filter { it.sourceId.equals(sourceId, true) }
    val ids = videos.mapTo(hashSetOf(), VideoUiModel::id)
    val playlists = snapshot.playlists.mapNotNull { playlist ->
        val members = playlist.videoIds.filter(ids::contains)
        val keepEmpty = playlist.videoIds.isEmpty() && playlist.sourceId.isBlank() && sourceId in emptyPlaylistSources
        if (members.isEmpty() && playlist.sourceId != sourceId && !keepEmpty) null else playlist.copy(
            id = if (playlist.sourceId.isNotBlank()) playlist.id else
                UUID.nameUUIDFromBytes("source-profile:$sourceId:$origin:${playlist.id}".toByteArray(Charsets.UTF_8)).toString(),
            videoIds = members, videoCount = if (members.isEmpty() && playlist.sourceId.isNotBlank()) playlist.videoCount else members.size,
        )
    }
    return LibraryImportSnapshot(videos, playlists)
}

/** Copy-on-write staging. Originals are read only; target transactions are durable and retryable. */
internal class SourceProfileSplit(context: Context) {
    private val app = context.applicationContext
    private val catalog = ProfileRepository(app)
    private val checkpoints = app.getSharedPreferences("grayjoy_source_profile_copies", Context.MODE_PRIVATE)

    suspend fun prepare(
        sources: List<SourceUiModel>, settings: GrayjayUiState,
        onProgress: (Int, Int) -> Unit,
        shareOffline: suspend (List<SourceProfileCopy>) -> Unit,
    ): Map<String, String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(sources.map { it.id }.containsAll(listOf("youtube", "spotify")))
            val originals = catalog.allProfiles().filter(::profileCopyEligible)
                .sortedBy { if (it.id == settings.activeProfileId) 1 else 0 }
            val eligibleIds = originals.mapTo(hashSetOf()) { it.id }
            val previousOrigins = checkpoints.getStringSet("pending_origins", emptySet()).orEmpty()
            check(catalog.sourceProfilesEnabled() || eligibleIds.containsAll(previousOrigins)) {
                app.getString(R.string.profile_split_scope_changed)
            }
            check(checkpoints.edit().putStringSet("pending_origins", eligibleIds).commit())
            val targets = sources.associate { source ->
                val target = catalog.stageSourceProfile(source.copy(accentColor = sourceProfileAccent(source)))
                val repository = SharedPreferencesSourceRepository(app, target.id)
                if (source.isCustom) repository.saveCustomSource(source)
                repository.setEnabled(source.id, true)
                if (!checkpoints.getBoolean("settings:${target.id}", false)) {
                    GrayjayPreferences(app, target.id).apply {
                        themeMode = settings.themeMode
                        holdToSpeedEnabled = settings.holdToSpeedEnabled
                        brainrotShortsEnabled = settings.brainrotShortsEnabled
                        sponsorBlockEnabled = settings.sponsorBlockEnabled
                        sponsorBlockAutomaticSkip = settings.sponsorBlockAutomaticSkip
                        youtubeAutoplayEnabled = settings.youtubeAutoplayEnabled
                    }
                    check(checkpoints.edit().putBoolean("settings:${target.id}", true).commit())
                }
                source.id to target.id
            }
            val total = originals.size * sources.size
            // Prefer the currently selected account; never combine or overwrite two logins.
            for (source in sources) {
                val target = targets.getValue(source.id)
                if (!GrayjayPluginAuthStore.has(app, target, source.engineId)) {
                    for (original in originals.sortedBy { if (it.id == settings.activeProfileId) 0 else 1 }) {
                        currentCoroutineContext().ensureActive()
                        if (!catalog.allProfiles().any { it.id == original.id && profileCopyEligible(it) }) continue
                        if (GrayjayPluginAuthStore.copyIfAbsent(app, original.id, target, source.engineId)) break
                    }
                }
            }
            var completed = 0
            onProgress(completed, total)
            for (original in originals) {
                currentCoroutineContext().ensureActive()
                // Validate the protection again immediately before opening any source library.
                if (!catalog.allProfiles().any { it.id == original.id && profileCopyEligible(it) }) continue
                val (library, known, followed) = synchronized(profileDataLock(app, original.id)) {
                    val sourcePreferences = GrayjayPreferences(app, original.id)
                    val snapshot = SharedPreferencesLibraryRepository(app, original.id).createImportSnapshot()
                    val known = sourcePreferences.loadImportedChannels() +
                        ChannelArtworkStore(app, original.id).load().map { it.first } +
                        snapshot.videos.mapNotNull { video ->
                            video.authorUrl.ifBlank { video.channelId }.takeIf(String::isNotBlank)?.let { id ->
                                ChannelUiModel(id, video.creator, video.sourceId, video.sourceName, 0, "", "", video.authorThumbnailUrl)
                            }
                        }
                    Triple(LibrarySnapshot(snapshot.videos, snapshot.playlists, 0), known, sourcePreferences.followedCreatorIds())
                }
                val emptyPlaylistSources = SharedPreferencesSourceRepository(app, original.id).storedEnabledSourceIds()
                    .orEmpty().ifEmpty { setOf("youtube") }
                for (source in sources) {
                    currentCoroutineContext().ensureActive()
                    val target = targets.getValue(source.id)
                    val checkpoint = "${original.id}:$target"
                    if (!checkpoints.getBoolean(checkpoint, false)) {
                        val part = partitionSourceLibrary(library, source.id, original.id, emptyPlaylistSources)
                        val subscriptions = (known.filter { it.sourceId == source.id && it.id in followed } +
                            followed.filter { id -> sourceReferenceMatches(id, source.id) && known.none { it.id == id } }
                                .map { id -> ChannelUiModel(id, id.substringAfterLast('/'), source.id, source.name, 0, "", "", "") }
                            ).distinctBy { it.id }
                        applyAccountImportTransaction(SharedPreferencesLibraryRepository(app, target),
                            GrayjayPreferences(app, target),
                            EngineUserImportResult(videos = part.videos, playlists = part.playlists, subscriptions = subscriptions,
                                historyCount = part.videos.count { it.lastWatchedAt > 0L }, warnings = emptyList()),
                            repairSyntheticHistoryDates = false)
                        shareOffline(listOf(SourceProfileCopy(original.id, target, part.videos.mapTo(hashSetOf()) { it.id })))
                        check(checkpoints.edit().putBoolean(checkpoint, true).commit())
                    }
                    onProgress(++completed, total)
                }
            }
            targets
        }
    }
    companion object { private val mutex = Mutex() }
}

internal fun sourceProfileAccent(source: SourceUiModel): Long = when (source.id.lowercase()) {
    "youtube" -> 0xFFE53935L
    "spotify" -> 0xFF1DB954L
    "crunchyroll" -> 0xFFF47521L
    "twitch" -> 0xFF9146FFL
    "nebula" -> 0xFF4DA1D7L
    else -> source.accentColor
}

private fun sourceReferenceMatches(id: String, source: String): Boolean {
    if (id.startsWith("$source:")) return true
    val host = runCatching { java.net.URI(id).host?.lowercase() }.getOrNull() ?: return false
    val domain = when (source) { "youtube" -> "youtube.com"; "spotify" -> "spotify.com"; "twitch" -> "twitch.tv"; "nebula" -> "nebula.tv"; else -> "$source.com" }
    return host == domain || host.endsWith(".$domain")
}
