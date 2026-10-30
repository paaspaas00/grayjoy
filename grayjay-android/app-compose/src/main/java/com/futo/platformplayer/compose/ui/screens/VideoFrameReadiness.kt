package com.futo.platformplayer.compose.ui.screens

import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** A surface's first frame only belongs to the requested video after its media item matches. */
@Stable
internal class VideoFrameReadiness(private val expectedId: String) {
    var itemId by mutableStateOf(""); private set
    var ready by mutableStateOf(false); private set
    fun mediaItemChanged(id: String) {
        if (itemId != id || id != expectedId) ready = false
        itemId = id
    }
    fun renderedFrame(id: String) {
        if (id == expectedId && itemId == expectedId) ready = true
    }
}

/** Allows native-view composition tests to supply a known synthetic frame without a decoder. */
internal val LocalVideoFrameReadiness = staticCompositionLocalOf<((String) -> Boolean)?> { null }

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun rememberVideoFrameReady(player: Player, videoId: String, surfaceKey: Any): Boolean {
    val override = LocalVideoFrameReadiness.current
    val state = remember(player, videoId, surfaceKey) { VideoFrameReadiness(videoId) }
    DisposableEffect(player, state) {
        state.mediaItemChanged(player.currentMediaItem?.mediaId.orEmpty())
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                state.mediaItemChanged(mediaItem?.mediaId.orEmpty())
            }
            override fun onRenderedFirstFrame() {
                if (player !is ExoPlayer) state.renderedFrame(player.currentMediaItem?.mediaId.orEmpty())
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                val timeline = eventTime.timeline
                val id = if (eventTime.windowIndex in 0 until timeline.windowCount)
                    timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId else ""
                state.renderedFrame(id)
            }
        }
        player.addListener(listener)
        (player as? ExoPlayer)?.addAnalyticsListener(analytics)
        onDispose { player.removeListener(listener); (player as? ExoPlayer)?.removeAnalyticsListener(analytics) }
    }
    return override?.invoke(videoId) ?: state.ready
}
