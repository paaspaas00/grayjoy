package com.futo.platformplayer.compose.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

internal fun Modifier.playerMorphViewport(
    expandedHeightPx: Float,
    minimizedHeightPx: Float?,
    minimizedTopPx: Float,
    isActive: Boolean,
    progress: () -> Float,
): Modifier = this
    // Translate the viewport AND its clip. A clip outside this layer stays at the
    // origin and cuts away the moving background/detail content during expansion.
    .graphicsLayer {
        alpha = if (isActive) 1f else 0f
        translationY = minimizedTopPx * progress().coerceIn(0f, 1f)
    }
    .clipToBounds()
    .layout { measurable, constraints ->
        // Only the viewport is resized per frame; expensive detail content keeps
        // stable expanded constraints and can reuse its measured layout.
        val expandedHeight = expandedHeightPx.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(
            constraints.copy(minHeight = expandedHeight, maxHeight = expandedHeight),
        )
        val viewportHeight = if (!isActive) 0 else transitionOverlayHeightPx(
            expandedHeightPx,
            minimizedHeightPx,
            progress(),
        ).roundToInt().coerceAtLeast(1)
        layout(placeable.width, viewportHeight) { placeable.place(0, 0) }
    }
