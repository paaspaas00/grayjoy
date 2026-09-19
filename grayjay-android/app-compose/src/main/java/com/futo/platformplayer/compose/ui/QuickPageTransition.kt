package com.futo.platformplayer.compose.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Navigation replaces the outgoing composition immediately, retaining only the incoming layer. */
@Composable
internal fun QuickPageTransition(
    targetKey: String,
    modifier: Modifier = Modifier,
    horizontalSlideDirection: Int = 0,
    animationRequest: Long = 0L,
    content: @Composable (String) -> Unit,
) {
    key(targetKey) {
        // Capture the one-shot navigation request. Its caller can clear the pending direction
        // immediately without snapping a transition that is already being drawn.
        val slideDirection = remember(animationRequest) { horizontalSlideDirection.coerceIn(-1, 1) }
        val progress = remember { Animatable(0f) }
        LaunchedEffect(animationRequest) {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(
                    durationMillis = if (slideDirection == 0) 140 else 220,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        Box(modifier.graphicsLayer {
            // Read animation state during drawing, not composition or measurement.
            alpha = if (slideDirection == 0) progress.value else 0.78f + 0.22f * progress.value
            translationX = size.width * slideDirection * (1f - progress.value)
            val scale = if (slideDirection == 0) {
                0.985f + 0.015f * progress.value
            } else {
                1f
            }
            scaleX = scale
            scaleY = scale
        }) {
            content(targetKey)
        }
    }
}
