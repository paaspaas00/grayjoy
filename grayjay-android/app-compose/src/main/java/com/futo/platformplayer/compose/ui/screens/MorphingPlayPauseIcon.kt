package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp

@Composable
internal fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val progress = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "play-pause-morph",
    )
    val outline = remember { Path() }
    Canvas(modifier.fillMaxSize().semantics { contentDescription = description }
        .testTag("player-play-pause-icon")) {
        // Two adjoining parts of the play triangle become the two pause bars.
        // One filled path avoids a seam at rest; only drawing is invalidated during the morph.
        val t = progress.value
        outline.reset()
        outline.moveTo(lerp(8f, 6f, t), 5f)
        outline.lineTo(lerp(13.5f, 10f, t), lerp(8.5f, 5f, t))
        outline.lineTo(lerp(13.5f, 10f, t), lerp(15.5f, 19f, t))
        outline.lineTo(lerp(8f, 6f, t), 19f)
        outline.close()
        outline.moveTo(lerp(13.5f, 14f, t), lerp(8.5f, 5f, t))
        outline.lineTo(lerp(19f, 18f, t), lerp(12f, 5f, t))
        outline.lineTo(lerp(19f, 18f, t), lerp(12f, 19f, t))
        outline.lineTo(lerp(13.5f, 14f, t), lerp(15.5f, 19f, t))
        outline.close()
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            drawPath(outline, tint)
        }
    }
}
