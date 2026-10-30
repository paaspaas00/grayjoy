package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.images.ArtworkCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@Composable
internal fun PrepareAdjacentMusicArtwork(currentId: String, order: List<String>, tracks: List<VideoUiModel>, active: Boolean) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { ArtworkCache.get(context) }
    val generation by cache.generation.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adjacent = remember(currentId, order, tracks) {
        val index = order.indexOf(currentId)
        if (index < 0) emptyList() else listOfNotNull(order.getOrNull(index - 1), order.getOrNull(index + 1))
            .mapNotNull { id -> tracks.firstOrNull { it.id == id } }
            .filter { it.thumbnailUrl.startsWith("https://") || it.thumbnailUrl.startsWith("http://") }
            .map { it.thumbnailUrl to it.thumbnailRequestHeaders }.distinct()
    }
    LaunchedEffect(active, currentId, adjacent, generation, lifecycle) {
        if (!active || adjacent.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    adjacent.forEach { (url, headers) -> launch {
                        cache.observe(url, headers).first { it?.file != null }
                    } }
                }
            }
        }
    }
}

internal data class MusicCoverFrame(val id: String, val url: String, val fallback: String?, val direction: Int)

internal fun musicCoverDirection(previous: String, next: String, currentQueue: List<String>, previousQueue: List<String>): Int {
    if (previous == next) return 0
    for (queue in listOf(currentQueue, previousQueue)) {
        val oldIndex = queue.indexOf(previous)
        val newIndex = queue.indexOf(next)
        if (oldIndex >= 0 && newIndex >= 0) return if (newIndex < oldIndex) -1 else 1
    }
    return 1
}

/** Navigation intent is matched to a target ID, so restarting a track cannot leave a stale
 * backward animation attached to the next automatic advance. No per-position state updates. */
internal class MusicCoverMotion(initialId: String, initialQueue: List<String>, private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private var currentId = initialId
    private var currentQueue = initialQueue
    private var direction = 1
    private data class Intent(val target: String, val direction: Int, val at: Long)
    private var intent: Intent? = null
    fun request(targetId: String?, direction: Int) { intent = targetId?.let { Intent(it, direction, clock()) } }
    fun frame(id: String, url: String, fallback: String?, queue: List<String>): MusicCoverFrame {
        if (id != currentId) {
            direction = intent?.takeIf { it.target == id && clock() - it.at in 0L..5000L }?.direction
                ?: musicCoverDirection(currentId, id, queue, currentQueue)
            currentId = id; intent = null
            currentQueue = queue
        } else if (queue !== currentQueue && id in queue) {
            currentQueue = queue
        }
        return MusicCoverFrame(id, url, fallback, direction)
    }
}

@Composable
internal fun AnimatedMusicArtwork(frame: MusicCoverFrame, animate: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(targetState = frame, contentKey = { it.id }, modifier = modifier.clip(RoundedCornerShape(12.dp)).clipToBounds(),
        transitionSpec = { musicTrackChangeTransition(targetState.direction, animate) }, label = "music-cover-change") { cover ->
        Box(Modifier.fillMaxSize().testTag("music-cover-${cover.id}")) {
            MusicArtwork(cover.url, Modifier.fillMaxSize(), large = true, fallbackUrl = cover.fallback)
        }
    }
}

private data class MusicTextFrame(val id: String, val text: String, val direction: Int)

@Composable
internal fun AnimatedMusicTrackText(
    frame: MusicCoverFrame,
    text: String,
    role: String,
    animate: Boolean,
    style: TextStyle,
    lines: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = MusicTextFrame(frame.id, text, frame.direction),
        contentKey = { it.id },
        modifier = modifier.fillMaxWidth().clipToBounds().testTag("music-$role-slot"),
        transitionSpec = { musicTrackChangeTransition(targetState.direction, animate) },
        label = "music-$role-change",
    ) { track ->
        Text(track.text, modifier = Modifier.fillMaxWidth().testTag("music-$role-${track.id}"),
            style = style, color = color, minLines = lines, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** Covers and metadata use one timing and direction policy, including external queue advances. */
private fun musicTrackChangeTransition(direction: Int, animate: Boolean): ContentTransform {
    if (!animate) return EnterTransition.None togetherWith ExitTransition.None
    return (fadeIn(tween(180, delayMillis = 30)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) {
        (it * .18f * direction).roundToInt()
    }).togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) {
        (-it * .18f * direction).roundToInt()
    })
}
