package com.futo.platformplayer.compose.recognition

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.material3.*
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.diagnostics.DiagnosticLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal enum class RecognitionOpenPhase { Preview, Opening, Completed }

internal data class RecognitionMorphNode(val bounds: androidx.compose.ui.geometry.Rect, val cornerDp: Float = 0f)
internal data class RecognitionMorphSnapshot(
    val source: Map<String, RecognitionMorphNode>, val target: Map<String, RecognitionMorphNode>,
)

internal fun recognitionMotionFraction(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

internal fun recognitionMorphRect(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect, progress: Float): androidx.compose.ui.geometry.Rect {
    val p = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return androidx.compose.ui.geometry.Rect(a.left + (b.left - a.left) * p, a.top + (b.top - a.top) * p,
        a.right + (b.right - a.right) * p, a.bottom + (b.bottom - a.bottom) * p)
}

@Stable
internal class RecognitionTransitionController(
    private val scope: CoroutineScope,
    private val resolve: suspend (String) -> VideoUiModel,
    private val open: (String, Boolean) -> Unit,
    private val allowed: () -> Boolean = { true },
    private val report: (String, Throwable) -> Unit = { message, error -> DiagnosticLog.e("SongRecognition", message, error) },
) {
    var match by mutableStateOf<RecognizedSong?>(null); private set
    var video by mutableStateOf<VideoUiModel?>(null); private set
    var phase by mutableStateOf(RecognitionOpenPhase.Preview); private set
    var resolving by mutableStateOf(false); private set
    var requested by mutableStateOf(false); private set
    var failures by mutableIntStateOf(0); private set
    val sourceNodes = mutableMapOf<String, RecognitionMorphNode>()
    val targetNodes = mutableMapOf<String, RecognitionMorphNode>()
    var morph by mutableStateOf<RecognitionMorphSnapshot?>(null); private set
    var progress by mutableFloatStateOf(0f); private set
    var handoff by mutableFloatStateOf(0f); private set
    var artworkFallback by mutableStateOf<Pair<String, String>?>(null); private set
    val motionRunning get() = phase == RecognitionOpenPhase.Opening && morph != null
    fun capture(part: String, node: RecognitionMorphNode, source: Boolean) {
        val rect = node.bounds
        if (motionRunning) return
        if (rect.width < 1 || rect.height < 1 || !rect.left.isFinite() || !rect.top.isFinite() ||
            !rect.right.isFinite() || !rect.bottom.isFinite()) {
            (if (source) sourceNodes else targetNodes).remove(part)
            return
        }
        (if (source) sourceNodes else targetNodes)[part] = node
    }
    fun destinationGeometry(): List<RecognitionMorphNode>? =
        listOfNotNull(targetNodes["surface"], targetNodes["art"]).takeIf { it.size == 2 }
    fun beginMotion(): Boolean {
        if (sourceNodes["surface"] == null || destinationGeometry() == null) return false
        morph = RecognitionMorphSnapshot(sourceNodes.toMap(), targetNodes.toMap())
        return true
    }
    fun move(value: Float) { progress = value.coerceIn(0f, 1f) }
    fun handOff(value: Float) { handoff = value.coerceIn(0f, 1f) }
    private var job: Job? = null
    private var generation = 0
    private var autoplay = false

    fun prepare(song: RecognizedSong, retry: Boolean = false) {
        if (!retry && match?.query == song.query) return
        job?.cancel(); val revision = ++generation
        match = song; video = null; resolving = true; phase = RecognitionOpenPhase.Preview
        job = scope.launch {
            try {
                val found = resolve(song.query)
                if (revision != generation) return@launch
                if (!allowed()) { resolving = false; requested = false; return@launch }
                video = found; resolving = false
                if (requested) commit()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (revision != generation) return@launch
                report("Could not prepare recognized song", error)
                resolving = false
                if (requested) { requested = false; failures++ }
            }
        }
    }
    fun openResult(playWhenReady: Boolean) {
        if (requested || phase != RecognitionOpenPhase.Preview) return
        val song = match ?: return
        autoplay = playWhenReady; requested = true
        if (video != null) commit() else if (!resolving) prepare(song, retry = true)
    }
    private fun commit() {
        if (!allowed()) { requested = false; return }
        val target = video ?: return
        try {
            artworkFallback = match?.artwork?.takeIf(String::isNotBlank)?.let { target.id to it }
            targetNodes.clear(); morph = null; progress = 0f; handoff = 0f
            phase = RecognitionOpenPhase.Opening
            open(target.id, autoplay)
        } catch (error: Exception) {
            report("Could not open recognized song", error)
            video = null; phase = RecognitionOpenPhase.Preview; requested = false; failures++
        }
    }
    fun complete() { phase = RecognitionOpenPhase.Completed; requested = false }
    fun reset() {
        generation++; job?.cancel(); job = null
        match = null; video = null; resolving = false; requested = false; phase = RecognitionOpenPhase.Preview
        sourceNodes.clear(); targetNodes.clear(); morph = null; progress = 0f; handoff = 0f
    }
}

internal val LocalRecognitionTransition = staticCompositionLocalOf<RecognitionTransitionController?> { null }
internal val LocalRecognitionDestinationActive = staticCompositionLocalOf { true }
internal typealias SongRecognizer = suspend ((RecognitionAudioFrame) -> Unit, () -> Unit) -> RecognizedSong?
internal val LocalSongRecognizer = staticCompositionLocalOf<SongRecognizer> { { level, searching -> SongRecognitionClient().recognize(level, searching) } }

/** Inputs are locked only for the short container transform, never while merely listening. */
@Composable
internal fun Modifier.recognitionControlsReveal(): Modifier {
    val opening = LocalRecognitionTransition.current?.phase == RecognitionOpenPhase.Opening
    return then(if (opening) Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
    } else Modifier)
}

@Composable
internal fun Modifier.recognitionDestinationLayer(): Modifier {
    val controller = LocalRecognitionTransition.current ?: return this
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }
        .then(if (controller.phase == RecognitionOpenPhase.Opening) Modifier.clearAndSetSemantics { } else Modifier).graphicsLayer {
        alpha = if (controller.phase == RecognitionOpenPhase.Opening)
            recognitionMotionFraction(controller.progress, .60f, .94f) else 1f
    }.drawWithContent {
        val morph = controller.morph
        if (controller.phase == RecognitionOpenPhase.Opening && morph != null) {
            val bounds = recognitionMorphRect(morph.source.getValue("surface").bounds,
                morph.target.getValue("surface").bounds, controller.progress).translate(-origin)
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) { this@drawWithContent.drawContent() }
        } else drawContent()
    }
}

@Composable
internal fun RecognitionTransitionHost(profileId: String, resolve: suspend (String) -> VideoUiModel,
    open: (String, Boolean) -> Unit, dismiss: () -> Unit, isVisible: () -> Boolean, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val latestResolve by rememberUpdatedState(resolve)
    val latestOpen by rememberUpdatedState(open)
    val latestDismiss by rememberUpdatedState(dismiss)
    val latestVisible by rememberUpdatedState(isVisible)
    val controller = remember(profileId) { RecognitionTransitionController(scope, { latestResolve(it) }, { id, play -> latestOpen(id, play) }, { latestVisible() }) }
    DisposableEffect(controller) { onDispose { controller.reset() } }
    LaunchedEffect(profileId) { latestDismiss() }
    LaunchedEffect(controller, controller.phase) {
        if (controller.phase == RecognitionOpenPhase.Opening) {
            // Mount and measure the real destination while the original sheet stays intact.
            // Destination capture is enabled only for the expanded player, never its mini slot.
            var previous: List<RecognitionMorphNode>? = null
            withTimeoutOrNull(1500) {
                while (true) {
                    withFrameNanos { }
                    val geometry = controller.destinationGeometry()
                    if (geometry != null && geometry == previous) break
                    previous = geometry
                }
            }
            if (latestVisible() && controller.beginMotion()) {
                animate(0f, 1f, animationSpec = tween(340, easing = FastOutSlowInEasing)) { value, _ -> controller.move(value) }
                // The live artwork/video is exposed only at identical final bounds.
                // Its crossfade cannot resize or restart an image request.
                animate(0f, 1f, animationSpec = tween(70, easing = LinearEasing)) { value, _ -> controller.handOff(value) }
            }
            controller.complete()
            latestDismiss()
        }
    }
    val context = LocalContext.current
    LaunchedEffect(controller.failures) {
        if (controller.failures > 0) android.widget.Toast.makeText(context, R.string.recognition_play_failed, android.widget.Toast.LENGTH_LONG).show()
    }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var windowSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    CompositionLocalProvider(LocalRecognitionTransition provides controller) {
        Box(Modifier.fillMaxSize().onGloballyPositioned {
            if (windowSize != androidx.compose.ui.unit.IntSize.Zero && windowSize != it.size && controller.phase == RecognitionOpenPhase.Opening) {
                controller.complete(); latestDismiss()
            }
            windowSize = it.size
            origin = it.positionInRoot()
        }) {
            content()
            if (controller.match != null && controller.phase != RecognitionOpenPhase.Completed) RecognitionMorphOverlay(controller, origin)
        }
    }
}

/** Geometry capture and visibility only. No nested scaling, lookahead or independent clocks. */
@Composable
internal fun RecognitionSharedPart(videoId: String?, part: String, modifier: Modifier = Modifier,
    fromSheet: Boolean = false, cornerRadius: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    val controller = LocalRecognitionTransition.current
    val card = com.futo.platformplayer.compose.ui.LocalPlayerCardTransition.current
    val destination = LocalRecognitionDestinationActive.current
    val matches = controller?.video?.id == videoId && videoId != null && destination
    Box(modifier.onGloballyPositioned { coordinates ->
        if (!fromSheet && destination) card?.capture(videoId, part, coordinates.boundsInRoot(), cornerRadius.value)
        if (controller != null && (fromSheet || (matches && controller.phase == RecognitionOpenPhase.Opening))) {
            controller.capture(part, RecognitionMorphNode(coordinates.boundsInRoot(), cornerRadius.value), fromSheet)
        }
    }.graphicsLayer {
        alpha = when {
            !fromSheet && card?.active == true && card.request?.source?.video?.id == videoId && part == "art" ->
                if (card.progress >= 1f) 1f else 0f
            controller == null -> 1f
            fromSheet && controller.phase == RecognitionOpenPhase.Completed -> 0f
            fromSheet && controller.motionRunning && part == "art" -> 0f
            fromSheet && controller.motionRunning && part == "surface" ->
                1f - recognitionMotionFraction(controller.progress, 0f, .20f)
            !fromSheet && matches && controller.phase == RecognitionOpenPhase.Opening && part == "art" ->
                if (controller.progress >= 1f) 1f else 0f
            else -> 1f
        }
    }, content = { content() })
}

@Composable
private fun RecognitionMorphOverlay(controller: RecognitionTransitionController, origin: androidx.compose.ui.geometry.Offset) {
    val snapshot = controller.morph
    val empty = RecognitionMorphNode(androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f))
    val sourceSurface = snapshot?.source?.get("surface") ?: empty
    val targetSurface = snapshot?.target?.get("surface") ?: empty
    val targetArt = snapshot?.target?.get("art") ?: empty
    val sourceArt = snapshot?.source?.get("art") ?: targetArt
    val hasSourceArt = snapshot?.source?.containsKey("art") != false
    val p = controller.progress
    val surface = recognitionMorphRect(sourceSurface.bounds, targetSurface.bounds, p).translate(-origin)
    val art = recognitionMorphRect(sourceArt.bounds, targetArt.bounds, p).translate(-origin)
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun rectModifier(rect: androidx.compose.ui.geometry.Rect): Modifier = with(density) {
        Modifier.offset { androidx.compose.ui.unit.IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(rect.width.toDp(), rect.height.toDp())
    }
    val surfaceAlpha = recognitionMotionFraction(p, 0f, .18f) * (1f - recognitionMotionFraction(p, .78f, 1f))
    Box(Modifier.fillMaxSize().zIndex(100f)
        .then(if (controller.phase == RecognitionOpenPhase.Opening) Modifier.pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
        } else Modifier)
        .graphicsLayer { alpha = if (controller.motionRunning) 1f else 0f }
        .testTag("recognition-morph-overlay")) {
        Box(rectModifier(surface).graphicsLayer { alpha = surfaceAlpha }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = (28f * (1f - p)).dp, topEnd = (28f * (1f - p)).dp))
            .background(androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface, p)))
        val corner = (sourceArt.cornerDp + (targetArt.cornerDp - sourceArt.cornerDp) * p).dp
        Box(rectModifier(art).graphicsLayer { alpha = (1f - controller.handoff) * if (hasSourceArt) 1f else recognitionMotionFraction(p, .55f, .9f) }.clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("recognition-morph-art")) {
            val sourceUrl = controller.match?.artwork.orEmpty()
            val targetUrl = controller.video?.thumbnailUrl.orEmpty().ifBlank { sourceUrl }
            if (sourceUrl.isBlank()) Icon(Icons.Outlined.MusicNote, null, Modifier.align(androidx.compose.ui.Alignment.Center).size(48.dp))
            else com.futo.platformplayer.compose.ui.screens.RemoteBitmapImage(sourceUrl, MaterialTheme.colorScheme.surfaceContainerHigh,
                Modifier.fillMaxSize(), requestSize = androidx.compose.ui.unit.IntSize(768, 768), cropToBounds = true)
            if (targetUrl.isNotBlank() && targetUrl != sourceUrl)
                com.futo.platformplayer.compose.ui.screens.RemoteBitmapImage(targetUrl, Color.Transparent,
                    Modifier.fillMaxSize().graphicsLayer { alpha = recognitionMotionFraction(p, .50f, .88f) },
                    requestHeaders = controller.video?.thumbnailRequestHeaders.orEmpty(),
                    requestSize = androidx.compose.ui.unit.IntSize(768, 768), showParentPlaceholderWhileLoading = true, cropToBounds = true)
        }
    }
}
