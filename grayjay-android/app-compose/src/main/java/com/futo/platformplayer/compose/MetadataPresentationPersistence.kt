package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.data.LibraryRepository
import com.futo.platformplayer.compose.ui.VideoUiModel

internal data class MetadataPresentationPatch(
    val duration: String? = null,
    val authorThumbnailUrl: String? = null,
    val isMusic: Boolean? = null,
) {
    fun merge(newer: MetadataPresentationPatch) = MetadataPresentationPatch(
        duration = newer.duration ?: duration,
        authorThumbnailUrl = newer.authorThumbnailUrl ?: authorThumbnailUrl,
        isMusic = newer.isMusic ?: isMusic,
    )
}

/** Main-thread-owned bounded queue; acknowledgements must not erase a newer partial patch. */
internal class MetadataPersistenceBuffer(
    private val maxEntriesPerProfile: Int = 512,
    private val maxProfiles: Int = 2,
) {
    init { require(maxEntriesPerProfile > 0 && maxProfiles > 0) }
    private val profiles = linkedMapOf<String, LinkedHashMap<String, MetadataPresentationPatch>>()

    fun put(profileId: String, videoId: String, patch: MetadataPresentationPatch) {
        val entries = profiles.getOrPut(profileId) { linkedMapOf() }
        entries[videoId] = entries[videoId]?.merge(patch) ?: patch
        while (entries.size > maxEntriesPerProfile) entries.remove(entries.keys.first())
        while (profiles.size > maxProfiles) profiles.remove(profiles.keys.first())
    }

    fun snapshot(profileId: String): Map<String, MetadataPresentationPatch> = profiles[profileId]?.toMap().orEmpty()

    fun acknowledge(profileId: String, saved: Map<String, MetadataPresentationPatch>) {
        val current = profiles[profileId] ?: return
        saved.forEach { (id, patch) -> if (current[id] == patch) current.remove(id) }
        if (current.isEmpty()) profiles.remove(profileId)
    }

    fun clear() = profiles.clear()
}

internal fun VideoUiModel.withMetadataPresentation(patch: MetadataPresentationPatch): VideoUiModel = copy(
    duration = patch.duration ?: duration,
    authorThumbnailUrl = patch.authorThumbnailUrl ?: authorThumbnailUrl,
    isMusic = patch.isMusic ?: isMusic,
)

/** The read and metadata-only merge share the repository's profile lock with all other writes. */
internal fun persistMetadataPresentations(
    repository: LibraryRepository,
    presentations: Map<String, MetadataPresentationPatch>,
    checkActive: () -> Unit = {},
): Int = synchronized(repository.transactionLock) {
    checkActive()
    val updates = repository.loadSavedVideos().mapNotNull { fresh ->
        presentations[fresh.id]?.let { fresh.withMetadataPresentation(it) }?.takeIf { it != fresh }
    }
    checkActive()
    if (updates.isNotEmpty()) repository.saveVideos(updates)
    updates.size
}
