package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.data.forLocalStorage
import com.futo.platformplayer.compose.ui.VideoUiModel

/** Keep navigation metadata, but do not retain every streamed video's manifests and HTTP clients. */
internal class RemoteVideoCache(private val descriptorLimit: Int = 8) : LinkedHashMap<String, VideoUiModel>() {
    private val descriptors = linkedSetOf<String>()
    override fun put(key: String, value: VideoUiModel): VideoUiModel? {
        val previous = super.put(key, value)
        descriptors.remove(key)
        if (value.playbackUrl.isNotBlank() || value.playbackManifest.isNotBlank() || value.qualityVariants.isNotEmpty()) {
            descriptors.add(key)
        }
        while (descriptors.size > descriptorLimit.coerceAtLeast(1)) {
            val oldest = descriptors.first()
            descriptors.remove(oldest)
            super.get(oldest)?.let { super.put(oldest, it.forLocalStorage()) }
        }
        return previous
    }
    override fun clear() { descriptors.clear(); super.clear() }
}
