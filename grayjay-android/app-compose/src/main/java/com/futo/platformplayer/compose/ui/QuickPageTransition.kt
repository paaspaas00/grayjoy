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
    content: @Composable (String) -> Unit,
) {
    key(targetKey) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            progress.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        }
        Box(modifier.graphicsLayer {
            // Read animation state during drawing, not composition or measurement.
            alpha = progress.value
            val scale = 0.985f + 0.015f * progress.value
            scaleX = scale
            scaleY = scale
        }) {
            content(targetKey)
        }
    }
}
