package com.futo.platformplayer.compose.playback

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSeekPreviewPlayback = staticCompositionLocalOf<(String, Boolean) -> Unit> { { _, _ -> } }

internal data class SeekPreviewIdentity(val videoId: String, val profileId: String,
    val openGeneration: Long, val queueGeneration: Long, val casting: Boolean)

/** A scrub owns a temporary pause, not permission to restart a different video/session. */
internal class SeekPreviewPause {
    private var identity: SeekPreviewIdentity? = null
    private var resume = false

    fun begin(key: SeekPreviewIdentity, playWhenReady: Boolean): Boolean {
        if (identity == key) return false
        identity = key
        resume = playWhenReady
        return playWhenReady
    }

    fun finish(videoId: String, current: SeekPreviewIdentity): Boolean {
        val before = identity ?: return false
        if (before.videoId != videoId) return false
        val shouldResume = before == current && resume
        cancel()
        return shouldResume
    }

    fun cancel() { identity = null; resume = false }
}
