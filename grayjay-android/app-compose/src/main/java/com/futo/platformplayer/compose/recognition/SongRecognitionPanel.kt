package com.futo.platformplayer.compose.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.diagnostics.DiagnosticLog
import com.futo.platformplayer.compose.ui.screens.RemoteBitmapImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
internal fun SongRecognitionPanel(visible: Boolean, onDismiss: () -> Unit,
    modifier: Modifier = Modifier) {
    val transition = remember { MutableTransitionState(false) }; transition.targetState = visible
    if (!transition.currentState && !transition.targetState) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var song by remember { mutableStateOf<RecognizedSong?>(null) }
    var error by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val controller = LocalRecognitionTransition.current
    val leftHanded = com.futo.platformplayer.compose.ui.LocalInteractionControls.current.leftHanded
    val recognizer = LocalSongRecognizer.current
    val playing = controller?.requested == true || controller?.phase == RecognitionOpenPhase.Opening
    var drag by remember { mutableFloatStateOf(0f) }
    var baseHeight by remember { mutableIntStateOf(0) }
    var renderedHeight by remember { mutableIntStateOf(0) }
    val expandedHeight by remember { derivedStateOf { (baseHeight - minOf(drag, 0f)).coerceAtLeast(0f) } }
    val density = LocalDensity.current
    val contentScroll = rememberScrollState()
    val level = remember { mutableStateOf(RecognitionAudioFrame.Silence) }
    com.futo.platformplayer.compose.ui.PageBackHandler(enabled = visible, onBack = onDismiss)
    DisposableEffect(Unit) { onDispose {
        if (controller?.phase != RecognitionOpenPhase.Opening) controller?.reset()
        onDismiss()
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; denied = !it }
    LaunchedEffect(visible) {
        if (visible && !granted) permission.launch(Manifest.permission.RECORD_AUDIO)
        if (!visible && controller?.phase != RecognitionOpenPhase.Opening) controller?.reset()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (granted) onDismiss() }
    LaunchedEffect(visible, granted, attempt) {
        if (!visible || !granted) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        song = null; error = false; finished = false; searching = false
        try { song = recognizer({ level.value = it; searching = false }, { searching = true }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { DiagnosticLog.e("SongRecognition", "Recognition failed", failure); error = true }
        finished = true
    }
    LaunchedEffect(song) { drag = 0f; baseHeight = 0; song?.let { controller?.prepare(it) } }
    val ready = song != null && controller?.phase == RecognitionOpenPhase.Preview && !playing
    val latestReady by rememberUpdatedState(ready)
    val latestController by rememberUpdatedState(controller)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val canDismiss by rememberUpdatedState(controller?.phase != RecognitionOpenPhase.Opening)
    val threshold = with(density) { 56.dp.toPx() }
    val swipe = remember(threshold) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!canDismiss || source != NestedScrollSource.UserInput) return Offset.Zero
            val dismissDirection = available.y > 0 && (drag != 0f || contentScroll.value == 0)
            val openingDirection = available.y < 0 && (drag > 0f || latestReady)
            if (!dismissDirection && !openingDirection) return Offset.Zero
            val minimum = if (latestReady || drag < 0f) -threshold * 3 else 0f
            drag = (drag + available.y).coerceIn(minimum, threshold * 4)
            return Offset(0f, available.y)
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (canDismiss && source == NestedScrollSource.UserInput && available.y > 0 && contentScroll.value == 0) {
                drag = (drag + available.y).coerceAtMost(threshold * 4)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!canDismiss || drag == 0f) return Velocity.Zero
            if (drag >= threshold || (drag > threshold * .35f && available.y > 900f)) {
                latestController?.reset(); latestDismiss()
            } else if (latestReady && (drag <= -threshold || (drag < -threshold * .35f && available.y < -900f))) {
                if (latestController?.video == null) {
                    // Keep the same visual size while waiting, with a fresh down-drag origin.
                    baseHeight = expandedHeight.toInt(); drag = 0f
                }
                latestController?.openResult(false)
            }
            else animate(drag, 0f, animationSpec = tween(180)) { value, _ -> drag = value }
            return available
        }
    } }
    val swipeLabel = stringResource(R.string.recognition_swipe_open)
    BoxWithConstraints(modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = transition, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(150)),
            exit = if (controller?.phase == RecognitionOpenPhase.Completed) ExitTransition.None else
                slideOutVertically(tween(230, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180))) {
            RecognitionSharedPart(null, "surface", Modifier.graphicsLayer { translationY = drag.coerceAtLeast(0f) }, fromSheet = true) {
            Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth()
                .heightIn(min = with(density) { expandedHeight.toDp() }.coerceAtMost(maxHeight), max = maxHeight)
                .onSizeChanged { renderedHeight = it.height; if (drag == 0f && !playing) baseHeight = it.height }
                .nestedScroll(swipe)
                .semantics { if (song != null) customActions = listOf(CustomAccessibilityAction(swipeLabel) { controller?.openResult(false); true }) }
                .testTag("song-recognition"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.verticalScroll(contentScroll).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(36.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f), RoundedCornerShape(2.dp)))
                    Text(stringResource(R.string.recognize_song), style = MaterialTheme.typography.titleLarge)
                    if (song == null) Text(stringResource(when {
                        denied -> R.string.recognition_permission
                        error -> R.string.recognition_error
                        finished -> R.string.recognition_no_match
                        searching -> R.string.recognition_searching
                        else -> R.string.recognition_listening
                    }), style = MaterialTheme.typography.bodyLarge)
                    AnimatedContent(song != null, label = "recognition-result") { found ->
                        if (found) song?.let { match ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                RecognitionSharedPart(null, "art", Modifier.size(160.dp).testTag("recognition-result-art"), fromSheet = true) {
                                    if (match.artwork.isNotBlank()) RemoteBitmapImage(match.artwork, MaterialTheme.colorScheme.surfaceVariant,
                                        Modifier.fillMaxSize(), requestSize = androidx.compose.ui.unit.IntSize(768, 768), cropToBounds = true) else Icon(Icons.Outlined.MusicNote, null, Modifier.fillMaxSize().padding(32.dp))
                                }
                                RecognitionSharedPart(null, "title", fromSheet = true) { Text(match.title, style = MaterialTheme.typography.headlineSmall) }
                                RecognitionSharedPart(null, "artist", fromSheet = true) { Text(match.artist, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(swipeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("recognition-swipe-hint"))
                            }
                        } else {
                            RecognitionWaveform(level, active = visible && granted && !finished && !searching)
                        }
                    }
                    Text(stringResource(R.string.recognition_privacy_short), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val close: @Composable () -> Unit = {
                        TextButton(onClick = { if (controller?.phase != RecognitionOpenPhase.Opening) controller?.reset(); onDismiss() },
                            enabled = controller?.phase != RecognitionOpenPhase.Opening,
                            modifier = Modifier.testTag("recognition-close")) { Text(stringResource(R.string.close)) }
                    }
                    val primary: @Composable () -> Unit = {
                        if (song != null) Button(enabled = !playing, onClick = { controller?.openResult(true) },
                            modifier = Modifier.testTag("recognition-play")) {
                            if (playing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.play))
                        }
                        else if (finished || denied) TextButton(onClick = {
                            if (!granted) permission.launch(Manifest.permission.RECORD_AUDIO) else attempt++
                        }) { Text(stringResource(R.string.retry)) }
                    }
                    Row(Modifier.fillMaxWidth().testTag("recognition-actions"),
                        horizontalArrangement = if (leftHanded) Arrangement.Absolute.Left else Arrangement.Absolute.Right) {
                        if (leftHanded) { primary(); close() } else { close(); primary() }
                    }
                }
            }
            }
        }
    }
}
