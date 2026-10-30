package com.futo.platformplayer.compose.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Draw-only motion: layout anchors and navigation clipping must stay stationary. */
internal fun Modifier.miniplayerDismissMotion(
    enabled: Boolean = true,
    fade: Boolean = true,
    progress: () -> Float,
): Modifier = graphicsLayer {
    val fraction = if (enabled) progress().coerceIn(0f, 1f) else 0f
    translationY = 24.dp.toPx() * fraction
    // Native video fades through PlayerView.alpha, not a Compose offscreen texture.
    alpha = if (fade) 1f - fraction else 1f
}
