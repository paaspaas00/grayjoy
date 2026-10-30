package com.futo.platformplayer.compose.ui

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.futo.platformplayer.compose.recognition.RecognitionMorphNode
import com.futo.platformplayer.compose.recognition.recognitionMorphRect
import com.futo.platformplayer.compose.recognition.recognitionMotionFraction
import com.futo.platformplayer.compose.ui.screens.RemoteBitmapImage
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

internal class CardMorphSource(val video: VideoUiModel, val music: Boolean, var previewSize: IntSize? = null,
    val titleFontSp: Float? = null, val containerColor: androidx.compose.ui.graphics.Color? = null,
    val destination: String = "video:${video.id}", val artworkCornerDp: Float = 12f,
    val previewFrame: android.graphics.Bitmap? = null, val surfaceCornerDp: Float = 18f) {
    val nodes = mutableMapOf<String, RecognitionMorphNode>()
    private val coordinates = mutableMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>()
    fun captureCoordinates(part: String, value: androidx.compose.ui.layout.LayoutCoordinates) { coordinates[part] = value }
    fun snapshot(): Map<String, RecognitionMorphNode> {
        // Compute root coordinates only on tap, not on every scroll frame for every card.
        for ((part, value) in coordinates) if (value.isAttached) capture(part, value.boundsInRoot()) else nodes.remove(part)
        return nodes.toMap()
    }
    fun capture(part: String, bounds: Rect) {
        if (bounds.width > 0 && bounds.height > 0) nodes[part] = RecognitionMorphNode(bounds, if (part == "art") artworkCornerDp else 0f)
        else nodes.remove(part)
        if (part == "art" && previewSize == null) previewSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1))
    }
}

internal data class CardMorphRequest(val source: CardMorphSource, val nodes: Map<String, RecognitionMorphNode>, val hasVisibleArtwork: Boolean)

@Stable
internal class PlayerCardTransition(private val nowMs: () -> Long = { SystemClock.uptimeMillis() }) {
    private var candidate: Pair<Long, CardMorphRequest>? = null
    var request by mutableStateOf<CardMorphRequest?>(null); private set
    val target = mutableMapOf<String, RecognitionMorphNode>()
    var geometry by mutableStateOf<Map<String, RecognitionMorphNode>?>(null); private set
    var progress by mutableFloatStateOf(0f); private set
    var handoff by mutableFloatStateOf(0f); private set
    val active get() = request != null
    fun arm(source: CardMorphSource) {
        val nodes = source.snapshot()
        val surface = nodes["surface"]?.bounds
        val hasArt = nodes["art"] != null
        val captured = if (surface != null && !hasArt) {
            // Numbered music rows have no cover. Grow a fading placeholder from their leading
            // slot instead of letting the final album art pop into view after the transform.
            val side = minOf(surface.height * .6f, surface.width * .15f)
            nodes + ("art" to RecognitionMorphNode(Rect(surface.left, surface.center.y - side / 2f,
                surface.left + side, surface.center.y + side / 2f)))
        } else nodes
        candidate = if (surface != null) nowMs() to CardMorphRequest(source, captured, hasArt) else null
    }
    fun begin(videoId: String) = beginDestination("video:$videoId")
    fun beginDestination(destination: String) {
        val armed = candidate; candidate = null
        if (armed == null || armed.second.source.destination != destination || nowMs() - armed.first > 1000) return
        target.clear(); geometry = null; progress = 0f; handoff = 0f; request = armed.second
    }
    fun capture(videoId: String?, part: String, bounds: Rect, corner: Float) {
        if (videoId != null) captureDestination("video:$videoId", part, bounds, corner)
    }
    fun captureDestination(destination: String, part: String, bounds: Rect, corner: Float) {
        if (geometry != null || request?.source?.destination != destination) return
        if (bounds.width > 0 && bounds.height > 0) target[part] = RecognitionMorphNode(bounds, corner)
    }
    fun measured(): List<RecognitionMorphNode>? {
        // The title can legitimately be outside a short viewport's lazy list. Its absence
        // must not delay the transform or turn it into a hard cut.
        val essential = listOfNotNull(target["surface"], target["art"])
        return if (essential.size == 2) essential + listOfNotNull(target["title"]) else null
    }
    fun freeze(): Boolean {
        if (measured() == null) return false
        geometry = target.toMap(); return true
    }
    fun move(value: Float) { progress = value }
    fun handOff(value: Float) { handoff = value }
    fun clear() { request = null; geometry = null; candidate = null; target.clear() }
}

internal val LocalPlayerCardTransition = staticCompositionLocalOf<PlayerCardTransition?> { null }

@Composable
internal fun PlayerCardTransitionHost(profileId: String, content: @Composable () -> Unit) {
    val motion = remember(profileId) { PlayerCardTransition() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { motion.clear() }
    LaunchedEffect(motion.request) {
        if (!motion.active) return@LaunchedEffect
        var previous: List<RecognitionMorphNode>? = null
        withTimeoutOrNull(700) {
            while (true) {
                withFrameNanos { }
                val next = motion.measured()
                if (next != null && next == previous) break
                previous = next
            }
        }
        if (motion.freeze()) {
            animate(0f, 1f, animationSpec = tween(320, easing = FastOutSlowInEasing)) { value, _ -> motion.move(value) }
            animate(0f, 1f, animationSpec = tween(65, easing = LinearEasing)) { value, _ -> motion.handOff(value) }
        }
        motion.clear()
    }
    CompositionLocalProvider(LocalPlayerCardTransition provides motion) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned {
            if (size != IntSize.Zero && size != it.size) motion.clear()
            size = it.size; origin = it.positionInRoot()
        }) {
            content()
            motion.request?.let { request -> CardMorphOverlay(motion, request, origin) }
        }
    }
}

@Composable
internal fun Modifier.cardDestinationLayer(destination: String? = null): Modifier {
    val motion = LocalPlayerCardTransition.current ?: return this
    val route = motion.request?.source?.destination ?: return this
    if (if (destination == null) !route.startsWith("video:") else route != destination) return this
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }
        .then(if (motion.active) Modifier.clearAndSetSemantics { } else Modifier).graphicsLayer {
        alpha = if (motion.active) recognitionMotionFraction(motion.progress, .56f, .94f) else 1f
    }.drawWithContent {
        val source = motion.request?.nodes?.get("surface")
        val target = motion.geometry?.get("surface")
        if (source != null && target != null) {
            val clip = recognitionMorphRect(source.bounds, target.bounds, motion.progress).translate(-origin)
            clipRect(clip.left, clip.top, clip.right, clip.bottom) { this@drawWithContent.drawContent() }
        } else drawContent()
    }
}

@Composable
internal fun Modifier.cardMorphTarget(destination: String, part: String, cornerDp: Float = 0f): Modifier {
    val motion = LocalPlayerCardTransition.current ?: return this
    val matching = motion.request?.source?.destination == destination
    return onGloballyPositioned { motion.captureDestination(destination, part, it.boundsInRoot(), cornerDp) }
        .graphicsLayer {
            alpha = when {
                !matching -> 1f
                part == "art" -> if (motion.progress >= 1f) 1f else 0f
                part == "title" -> recognitionMotionFraction(motion.progress, .56f, .94f)
                else -> 1f
            }
        }
}

@Composable
private fun CardMorphOverlay(motion: PlayerCardTransition, request: CardMorphRequest, origin: Offset) {
    val p = motion.progress
    val source = request.nodes
    val target = motion.geometry ?: source
    val density = LocalDensity.current
    fun rect(part: String): Rect? = source[part]?.let { from ->
        recognitionMorphRect(from.bounds, target[part]?.bounds ?: from.bounds, p).translate(-origin)
    }
    fun place(rect: Rect): Modifier = with(density) {
        Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }.size(rect.width.toDp(), rect.height.toDp())
    }
    val surface = rect("surface") ?: return
    val art = rect("art")
    val title = rect("title")
    val surfaceColor = MaterialTheme.colorScheme.surface
    val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    Box(Modifier.fillMaxSize().zIndex(100f).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
    }.testTag("card-player-morph")) {
        Box(place(surface).graphicsLayer { alpha = 1f - recognitionMotionFraction(p, .78f, 1f) }
            .clip(RoundedCornerShape((request.source.surfaceCornerDp * (1f - p)).dp))
            .background(androidx.compose.ui.graphics.lerp(request.source.containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow, surfaceColor, p))
            .testTag("card-player-morph-surface"))
        if (art != null) Box(place(art).zIndex(2f).graphicsLayer {
            alpha = (1f - motion.handoff) * if (request.hasVisibleArtwork) 1f else recognitionMotionFraction(p, .05f, .35f)
        }
            .clip(RoundedCornerShape((request.source.artworkCornerDp + ((target["art"]?.cornerDp ?: 0f) - request.source.artworkCornerDp) * p).dp))
            .background(if (request.source.previewFrame != null) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh).testTag("card-player-morph-art")) {
            val video = request.source.video
            if (request.source.previewFrame != null) Image(request.source.previewFrame.asImageBitmap(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (video.thumbnailUrl.isNotBlank()) {
                // A single progressive image keeps the cached preview while the full decode
                // arrives. Both use uniform cropping; two overlaid decodes caused ghost images.
                RemoteBitmapImage(video.thumbnailUrl, MaterialTheme.colorScheme.surfaceContainerHigh,
                    Modifier.fillMaxSize(), requestHeaders = video.thumbnailRequestHeaders,
                    requestSize = IntSize(768, 768), cropToBounds = true,
                    previewRequestSize = request.source.previewSize?.takeIf { maxOf(it.width, it.height) <= 384 },
                    fallbackUrl = com.futo.platformplayer.compose.ui.screens.youtubeThumbnailFallbackUrl(video.sourceId, video.id, video.thumbnailUrl))
            }
        }
        if (title != null) Text(request.source.video.title,
            modifier = place(title).zIndex(3f).graphicsLayer { alpha = 1f - recognitionMotionFraction(p, .05f, .25f) },
            style = (if (request.source.music) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall).let {
                request.source.titleFontSp?.let { size -> it.copy(fontSize = size.sp) } ?: it
            },
            color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val targetTitle = target["title"]?.bounds?.translate(-origin)
        if (motion.geometry != null && targetTitle != null && request.source.destination.startsWith("video:")) Canvas(Modifier.fillMaxSize().graphicsLayer {
            alpha = recognitionMotionFraction(p, .12f, .42f) * (1f - recognitionMotionFraction(p, .5f, .8f))
        }) {
            val top = targetTitle.bottom + 12.dp.toPx()
            clipRect(surface.left, surface.top, surface.right, surface.bottom) {
                drawRoundRect(skeletonColor, Offset(targetTitle.left, top), androidx.compose.ui.geometry.Size(targetTitle.width * .65f, 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                drawRoundRect(skeletonColor, Offset(targetTitle.left, top + 30.dp.toPx()), androidx.compose.ui.geometry.Size(targetTitle.width, 46.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
            }
        }
    }
}
