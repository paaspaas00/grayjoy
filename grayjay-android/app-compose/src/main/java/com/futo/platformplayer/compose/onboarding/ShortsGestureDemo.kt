package com.futo.platformplayer.compose.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

internal data class ShortsGestureFrame(
    val pageOffset: Float,
    val fingerY: Float,
    val handAlpha: Float,
    val contactAlpha: Float,
    val towardNext: Boolean,
)

/** Two deliberate gestures: approach, touch, drag, release/snap, then a reading pause. */
internal fun shortsGestureFrame(progress: Float): ShortsGestureFrame {
    val phase = (progress.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f) % 1f * 2f
    val returning = phase >= 1f
    val time = phase - if (returning) 1f else 0f
    fun ramp(start: Float, end: Float): Float {
        val t = ((time - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    val drag = ramp(.22f, .57f)
    val displacement = .51f * drag + .49f * ramp(.57f, .71f)
    return ShortsGestureFrame(
        pageOffset = if (returning) 1f - displacement else displacement,
        fingerY = if (returning) .24f + .51f * drag else .75f - .51f * drag,
        handAlpha = ramp(.02f, .12f) * (1f - ramp(.58f, .71f)),
        contactAlpha = ramp(.12f, .20f) * (1f - ramp(.56f, .64f)),
        towardNext = !returning,
    )
}

@Composable
internal fun ShortsGestureDemo(active: Boolean) {
    val progress: State<Float> = if (active && ValueAnimator.areAnimatorsEnabled()) {
        rememberInfiniteTransition(label = "shorts-hand-demo").animateFloat(
            0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
            label = "touch-drag-release",
        )
    } else rememberUpdatedState(.095f)
    val towardNext by remember(progress) { derivedStateOf { shortsGestureFrame(progress.value).towardNext } }
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.setup_shorts_animation)
    val hand = remember {
        Path().apply {
            moveTo(15f, 35f)
            lineTo(15f, 6f)
            cubicTo(15f, -1f, 25f, -1f, 25f, 6f)
            lineTo(25f, 26f)
            cubicTo(25f, 20f, 35f, 20f, 35f, 28f)
            cubicTo(36f, 23f, 44f, 25f, 44f, 32f)
            cubicTo(46f, 28f, 53f, 31f, 53f, 38f)
            lineTo(52f, 46f)
            cubicTo(52f, 53f, 47f, 58f, 44f, 61f)
            lineTo(44f, 68f)
            lineTo(20f, 68f)
            lineTo(19f, 61f)
            cubicTo(17f, 59f, 12f, 53f, 8f, 48f)
            lineTo(1f, 38f)
            cubicTo(-2f, 32f, 4f, 28f, 8f, 32f)
            lineTo(15f, 39f)
            close()
        }
    }
    val screenClip = remember { Path() }
    Column(
        Modifier.fillMaxWidth().height(216.dp).testTag("shorts-gesture-demo")
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Animation state is read in drawing, not in composition/layout on each frame.
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val frame = shortsGestureFrame(progress.value)
            val height = size.height * .90f
            val width = height * .56f
            val left = (size.width - width) / 2f - 8.dp.toPx()
            val top = 4.dp.toPx()
            val corner = CornerRadius(17.dp.toPx())
            drawCircle(colors.primary.copy(alpha = .055f), height * .56f, Offset(size.width / 2, height * .52f))
            drawRoundRect(Color.Black.copy(alpha = .12f), Offset(left + 2.dp.toPx(), top + 3.dp.toPx()), Size(width, height), corner)
            drawRoundRect(colors.surfaceContainerHighest, Offset(left, top), Size(width, height), corner)
            drawRoundRect(colors.outlineVariant, Offset(left, top), Size(width, height), corner, style = Stroke(1.dp.toPx()))
            val inset = 5.dp.toPx()
            val screen = Rect(left + inset, top + inset, left + width - inset, top + height - inset)
            screenClip.reset()
            screenClip.addRoundRect(RoundRect(screen, CornerRadius(12.dp.toPx())))
            clipPath(screenClip) {
                for (index in 0..1) {
                    val y = screen.top + (index - frame.pageOffset) * screen.height
                    val background = if (index == 0) colors.primaryContainer else colors.tertiaryContainer
                    val ink = if (index == 0) colors.onPrimaryContainer else colors.onTertiaryContainer
                    drawRect(background, Offset(screen.left, y), screen.size)
                    drawCircle(ink.copy(alpha = .09f), screen.width * .5f,
                        Offset(screen.left + screen.width * (if (index == 0) .23f else .83f), y + screen.height * .27f))
                    val center = Offset(screen.center.x, y + screen.height * .42f)
                    drawCircle(ink.copy(alpha = .09f), 19.dp.toPx(), center)
                    drawPath(Path().apply {
                        moveTo(center.x - 5.dp.toPx(), center.y - 9.dp.toPx())
                        lineTo(center.x + 9.dp.toPx(), center.y)
                        lineTo(center.x - 5.dp.toPx(), center.y + 9.dp.toPx())
                        close()
                    }, ink)
                    drawCircle(ink.copy(alpha = .45f), 5.dp.toPx(),
                        Offset(screen.left + 10.dp.toPx(), y + screen.height * .79f))
                    drawRoundRect(ink.copy(alpha = .38f), Offset(screen.left + 20.dp.toPx(), y + screen.height * .77f),
                        Size(screen.width * .50f, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawRoundRect(ink.copy(alpha = .18f), Offset(screen.left + 7.dp.toPx(), y + screen.height * .88f),
                        Size(screen.width * .65f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
            drawRoundRect(colors.surfaceContainerHighest, Offset(left + width / 2 - 8.dp.toPx(), top + 3.dp.toPx()),
                Size(16.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            val tip = Offset(screen.left + screen.width * .62f, screen.top + screen.height * frame.fingerY)
            drawCircle(colors.primary.copy(alpha = .14f * frame.contactAlpha), 13.dp.toPx(), tip)
            drawCircle(colors.primary.copy(alpha = .65f * frame.contactAlpha), 9.dp.toPx(), tip, style = Stroke(1.5.dp.toPx()))
            if (frame.handAlpha > 0f) {
                val handScale = width * .50f / 54f * (1f - frame.contactAlpha * .035f)
                val lift = (1f - frame.handAlpha) * 7.dp.toPx()
                withTransform({
                    translate(tip.x - 20f * handScale + lift, tip.y - 3f * handScale + lift)
                    scale(handScale, handScale, Offset.Zero)
                }) {
                    translate(1.5f, 2f) { drawPath(hand, Color.Black.copy(alpha = .18f * frame.handAlpha)) }
                    drawPath(hand, colors.inverseSurface.copy(alpha = frame.handAlpha))
                    drawPath(hand, colors.inverseOnSurface.copy(alpha = .88f * frame.handAlpha), style = Stroke(1.8f))
                }
            }
        }
        Text(stringResource(if (towardNext) R.string.setup_shorts_next else R.string.setup_shorts_previous),
            style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
}
