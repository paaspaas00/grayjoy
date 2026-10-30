package com.futo.platformplayer.compose.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Ordinary navigation replaces immediately; a card morph retains at most one outgoing page. */
@Composable
internal fun QuickPageTransition(
    targetKey: String,
    modifier: Modifier = Modifier,
    horizontalSlideDirection: Int = 0,
    animationRequest: Long = 0L,
    animate: Boolean = true,
    content: @Composable (String) -> Unit,
) {
    val constrained = com.futo.platformplayer.compose.rememberDevicePerformanceProfile().isLowEnd
    val morph = LocalPlayerCardTransition.current
    val morphing = morph?.request?.source?.destination == targetKey
    var pages by remember { mutableStateOf(listOf(targetKey)) }
    if (pages.last() != targetKey) pages = if (morphing) listOf(pages.last(), targetKey) else listOf(targetKey)
    LaunchedEffect(targetKey, morphing) { if (!morphing && pages.size > 1) pages = listOf(targetKey) }
    Box(modifier) { pages.forEach { pageKey -> key(pageKey) {
        val outgoing = pageKey != targetKey
        // Capture the one-shot navigation request. Its caller can clear the pending direction
        // immediately without snapping a transition that is already being drawn.
        val slideDirection = remember(animationRequest) { horizontalSlideDirection.coerceIn(-1, 1) }
        val animateThisPage = remember { animate && !morphing && (!constrained || slideDirection != 0) }
        val progress = remember { Animatable(if (animateThisPage) 0f else 1f) }
        LaunchedEffect(animationRequest) {
            if (!animateThisPage) return@LaunchedEffect
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(
                    durationMillis = if (slideDirection == 0) 140 else 220,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        Box(Modifier.fillMaxSize().cardMorphTarget(pageKey, "surface").cardDestinationLayer(pageKey)
            .then(if (outgoing) Modifier.clearAndSetSemantics { } else Modifier).graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0f)
            // Read animation state during drawing, not composition or measurement.
            alpha = if (outgoing) 1f - com.futo.platformplayer.compose.recognition.recognitionMotionFraction(morph?.progress ?: 1f, .45f, .9f)
                else if (constrained) 1f else if (slideDirection == 0) progress.value else 0.78f + 0.22f * progress.value
            translationX = if (slideDirection == 0) 0f else size.width * slideDirection * (1f - progress.value)
            val scale = if (slideDirection == 0 && !constrained) {
                0.985f + 0.015f * progress.value
            } else {
                1f
            }
            scaleX = scale
            scaleY = scale
        }) {
            content(pageKey)
        }
    } } }
}
