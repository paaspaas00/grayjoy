package com.futo.platformplayer.compose.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

/** Only the visible, foreground page owns a clock. Frames invalidate Canvas, not the pager. */
@Composable
internal fun setupArtworkPhase(active: Boolean, duration: Int, restPhase: Float = .2f): State<Float> {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return if (active && resumed) rememberInfiniteTransition(label = "setup-artwork")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(duration, easing = LinearEasing)),
            label = "setup-artwork-phase")
    else rememberUpdatedState(restPhase)
}

internal fun setupRecognitionReveal(phase: Float): Float {
    val value = phase.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    val enter = ((value - .52f) / .13f).coerceIn(0f, 1f)
    val exit = ((1f - value) / .12f).coerceIn(0f, 1f)
    return minOf(enter, exit).let { it * it * (3f - 2f * it) }
}

internal fun setupHandControlX(left: Boolean): Float = if (left) 56f else 104f

@Composable
internal fun HandednessArtwork(left: Boolean, selected: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val phase = setupArtworkPhase(active, 2800)
    val hand = remember {
        Path().apply {
            moveTo(8f, 29f); lineTo(8f, 7f)
            cubicTo(8f, 0f, 18f, 0f, 18f, 7f); lineTo(18f, 22f)
            cubicTo(23f, 18f, 29f, 22f, 28f, 27f)
            cubicTo(34f, 24f, 39f, 28f, 38f, 33f)
            cubicTo(43f, 31f, 48f, 35f, 46f, 43f)
            lineTo(43f, 54f); lineTo(40f, 64f); lineTo(16f, 64f)
            cubicTo(12f, 57f, 7f, 51f, 2f, 45f)
            cubicTo(-7f, 35f, -1f, 30f, 5f, 35f)
            lineTo(8f, 38f); close()
        }
    }
    Canvas(modifier.testTag(if (left) "setup-hand-art-left" else "setup-hand-art-right")) {
        val scale = minOf(size.width / 160f, size.height / 164f)
        withTransform({ translate((size.width - 160f * scale) / 2, (size.height - 164f * scale) / 2); scale(scale, scale, Offset.Zero) }) {
            val pulse = if (active) (.5f - .5f * cos(phase.value * 2f * PI.toFloat())) else 0f
            val ink = if (selected) colors.primary else colors.onSurfaceVariant
            drawCircle(ink.copy(alpha = .06f), 65f, Offset(80f, 76f))
            drawRoundRect(Color.Black.copy(alpha = .08f), Offset(37f, 7f), Size(90f, 140f), CornerRadius(17f))
            drawRoundRect(colors.surface, Offset(35f, 4f), Size(90f, 140f), CornerRadius(17f))
            drawRoundRect(colors.outlineVariant, Offset(35f, 4f), Size(90f, 140f), CornerRadius(17f), style = Stroke(1.5f))
            drawRoundRect(colors.onSurface.copy(alpha = .35f), Offset(69f, 11f), Size(22f, 3f), CornerRadius(2f))
            drawRoundRect(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer),
                Offset(41f, 27f), Offset(119f, 80f)), Offset(41f, 25f), Size(78f, 52f), CornerRadius(9f))
            drawCircle(colors.onPrimaryContainer.copy(alpha = .13f), 14f, Offset(98f, 43f))
            drawPath(Path().apply { moveTo(44f, 72f); lineTo(65f, 49f); lineTo(82f, 66f); lineTo(94f, 55f); lineTo(116f, 72f); close() },
                colors.primary.copy(alpha = .24f))
            drawPlay(Offset(80f, 51f), 7f, colors.onPrimaryContainer)
            drawLine(colors.outlineVariant, Offset(45f, 85f), Offset(115f, 85f), 2f, StrokeCap.Round)
            drawLine(ink, Offset(45f, 85f), Offset(74f, 85f), 2f, StrokeCap.Round)
            drawCircle(ink, 3f, Offset(74f, 85f))
            val x = setupHandControlX(left)
            drawRoundRect(ink.copy(alpha = .1f), Offset(x - 11f, 95f), Size(22f, 37f), CornerRadius(11f))
            drawCircle(ink, 8f, Offset(x, 106f))
            drawLine(colors.surface, Offset(x - 3f, 106f), Offset(x + 3f, 106f), 1.5f, StrokeCap.Round)
            drawLine(colors.surface, Offset(x, 103f), Offset(x, 109f), 1.5f, StrokeCap.Round)
            drawCircle(ink.copy(alpha = .6f), 3f, Offset(x, 122f))
            val textX = if (left) 76f else 47f
            drawLine(colors.onSurface.copy(alpha = .38f), Offset(textX, 102f), Offset(textX + 24f, 102f), 3f, StrokeCap.Round)
            drawLine(colors.onSurface.copy(alpha = .16f), Offset(textX, 112f), Offset(textX + 19f, 112f), 3f, StrokeCap.Round)
            drawCircle(ink.copy(alpha = .18f * (1f - pulse)), 12f + 12f * pulse, Offset(x, 106f), style = Stroke(2f))
            val handScale = .76f - .025f * pulse
            withTransform({
                translate(x - (if (left) -13f else 13f) * handScale, 102f + 3f * (1f - pulse))
                scale(if (left) -handScale else handScale, handScale, Offset.Zero)
            }) {
                drawPath(hand, colors.surfaceContainerHighest)
                drawPath(hand, ink, style = Stroke(2f, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
internal fun SongRecognitionArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val phase = setupArtworkPhase(active, 5200)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Canvas(Modifier.widthIn(max = 338.dp).fillMaxWidth().aspectRatio(1.8f).testTag("setup-recognition-art")) {
        val scale = minOf(size.width / 360f, size.height / 200f)
        withTransform({ translate((size.width - 360f * scale) / 2, (size.height - 200f * scale) / 2); scale(scale, scale, Offset.Zero) }) {
            val t = phase.value
            val found = setupRecognitionReveal(t)
            drawRoundRect(colors.surfaceContainerLow, Offset(0f, 4f), Size(360f, 192f), CornerRadius(28f))
            drawCircle(colors.primary.copy(alpha = .06f), 74f, Offset(88f, 93f))
            drawCircle(colors.tertiary.copy(alpha = .07f), 35f, Offset(306f, 43f))
            rotate(-9f, Offset(105f, 94f)) {
                drawRoundRect(Color.Black.copy(alpha = .10f), Offset(48f, 27f), Size(118f, 136f), CornerRadius(19f))
                drawRoundRect(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer),
                    Offset(45f, 23f), Offset(163f, 159f)), Offset(45f, 23f), Size(118f, 136f), CornerRadius(19f))
                drawCircle(colors.onPrimaryContainer.copy(alpha = .86f), 40f, Offset(104f, 81f))
                for (radius in listOf(29f, 34f)) drawCircle(colors.primaryContainer.copy(alpha = .2f), radius, Offset(104f, 81f), style = Stroke(1f))
                drawCircle(colors.primaryContainer, 14f, Offset(104f, 81f))
                drawCircle(colors.onPrimaryContainer, 3f, Offset(104f, 81f))
                drawLine(colors.onPrimaryContainer.copy(alpha = .65f), Offset(64f, 136f), Offset(108f, 136f), 4f, StrokeCap.Round)
                drawLine(colors.onPrimaryContainer.copy(alpha = .25f), Offset(64f, 146f), Offset(94f, 146f), 3f, StrokeCap.Round)
            }
            val y = 77f - 4f * found
            drawRoundRect(Color.Black.copy(alpha = .08f), Offset(111f, y + 4f), Size(227f, 96f), CornerRadius(23f))
            drawRoundRect(colors.surfaceContainerLowest, Offset(109f, y), Size(227f, 96f), CornerRadius(23f))
            drawRoundRect(colors.outlineVariant.copy(alpha = .65f), Offset(109f, y), Size(227f, 96f), CornerRadius(23f), style = Stroke(1f))
            if (found < 1f) {
                val alpha = 1f - found
                val travel = (t / .52f).coerceIn(0f, 1f)
                repeat(21) { i ->
                    val distance = i / 20f - travel
                    val envelope = exp(-distance * distance * 13f)
                    val beat = .5f + .5f * sin(t * 42f + i * .7f)
                    val h = 3f + 22f * envelope * (.35f + .65f * beat)
                    val x = 131f + i * 9f
                    drawLine(colors.primary.copy(alpha = alpha), Offset(x, y + 48f - h), Offset(x, y + 48f + h), 4f, StrokeCap.Round)
                }
            }
            if (found > 0f) {
                val ink = colors.primary.copy(alpha = found)
                drawRoundRect(colors.primaryContainer.copy(alpha = found), Offset(128f, y + 21f), Size(54f, 54f), CornerRadius(12f))
                drawNote(Offset(155f, y + 49f), 17f, colors.onPrimaryContainer.copy(alpha = found))
                drawLine(colors.onSurface.copy(alpha = .85f * found), Offset(198f, y + 35f), Offset(291f, y + 35f), 6f, StrokeCap.Round)
                drawLine(colors.onSurfaceVariant.copy(alpha = .45f * found), Offset(198f, y + 50f), Offset(255f, y + 50f), 4f, StrokeCap.Round)
                drawLine(ink.copy(alpha = .5f * found), Offset(198f, y + 66f), Offset(236f, y + 66f), 3f, StrokeCap.Round)
                drawCircle(colors.primary.copy(alpha = found), 15f * (.85f + .15f * found), Offset(316f, y + 5f))
                drawLine(colors.onPrimary.copy(alpha = found), Offset(310f, y + 5f), Offset(314f, y + 9f), 2.5f, StrokeCap.Round)
                drawLine(colors.onPrimary.copy(alpha = found), Offset(314f, y + 9f), Offset(322f, y + 1f), 2.5f, StrokeCap.Round)
            }
            drawNote(Offset(235f, 34f), 12f, colors.primary.copy(alpha = .65f))
            drawNote(Offset(26f, 161f), 9f, colors.tertiary.copy(alpha = .6f))
        }
    }
    }
}

private fun DrawScope.drawPlay(center: Offset, radius: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(center.x - radius * .55f, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x - radius * .55f, center.y + radius); close()
    }, color)
}

private fun DrawScope.drawNote(center: Offset, radius: Float, color: Color) {
    val x = center.x + radius * .25f
    drawLine(color, Offset(x, center.y + radius * .4f), Offset(x, center.y - radius * .7f), radius * .18f, StrokeCap.Round)
    drawLine(color, Offset(x, center.y - radius * .7f), Offset(x + radius * .6f, center.y - radius * .45f), radius * .23f, StrokeCap.Round)
    drawOval(color, Offset(x - radius * .75f, center.y + radius * .1f), Size(radius * .85f, radius * .6f))
}
