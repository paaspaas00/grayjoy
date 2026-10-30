package com.futo.platformplayer.compose.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

/** A single entrance using the original transparent artwork and its negative-space Play mark. */
@Composable
internal fun GrayjoyWelcomeLogo(active: Boolean) {
    val animate = ValueAnimator.areAnimatorsEnabled()
    // Reset before drawing, not one frame after the returning page becomes visible.
    val timeline = remember(active, animate) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(active, animate) {
        if (active && animate) {
            timeline.animateTo(1f, tween(3_000, easing = LinearEasing))
        }
    }
    val bird = ImageBitmap.imageResource(R.drawable.grayjoy_logo_foreground)
    val description = stringResource(R.string.app_name)
    val blue = remember { Brush.verticalGradient(listOf(Color(0xFF2478E8), Color(0xFF082A66)), endY = 512f) }
    val wing = remember {
        Brush.verticalGradient(listOf(Color(0xFF586D7C), Color(0xFF5B707F)), startY = 162f, endY = 276f)
    }
    val play = remember {
        Path().apply {
            moveTo(213f, 165f)
            lineTo(286f, 210f)
            cubicTo(294f, 214f, 294f, 223f, 286f, 228f)
            lineTo(214f, 273f)
            cubicTo(205f, 279f, 196f, 275f, 196f, 265f)
            lineTo(196f, 174f)
            cubicTo(196f, 163f, 204f, 158f, 213f, 165f)
            close()
        }
    }
    val outline = remember { Path() }
    val measure = remember(play) { PathMeasure().apply { setPath(play, true) } }
    Box(
        Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(136.dp).clipToBounds().testTag("welcome-grayjoy-logo")) {
            if (!active) return@Canvas
            // Reading the clock here redraws only this small canvas, without relaying out the guide.
            val time = timeline.value
            fun fraction(start: Float, end: Float) = ((time - start) / (end - start)).coerceIn(0f, 1f)
            val circleProgress = FastOutSlowInEasing.transform(fraction(0f, .25f))
            val arrival = FastOutSlowInEasing.transform(fraction(.16f, .56f))
            val tracing = fraction(.57f, .79f)
            val filling = FastOutSlowInEasing.transform(fraction(.81f, 1f))
            withTransform({ scale(size.width / 512f, size.height / 512f, Offset.Zero) }) {
                withTransform({ scale(.74f + .26f * circleProgress, .74f + .26f * circleProgress, Offset(256f, 256f)) }) {
                    drawCircle(blue, 256f, Offset(256f, 256f), alpha = circleProgress)
                }
                withTransform({
                    translate(25.6f - 160f * (1f - arrival), 25.6f)
                    scale(.9f, .9f, Offset.Zero)
                }) {
                    // Temporarily close the transparent Play aperture while the bird arrives.
                    // Fading this cover reveals the original blue aperture without altering the asset.
                    if (filling < 1f) drawPath(play, wing, alpha = arrival * (1f - filling))
                    drawImage(bird, alpha = arrival)
                    if (tracing > 0f && filling < 1f) {
                        outline.reset()
                        measure.getSegment(0f, measure.length * tracing, outline, startWithMoveTo = true)
                        drawPath(outline, Color(0xFF83DBFF), alpha = 1f - filling,
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}
