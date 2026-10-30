package com.futo.platformplayer.compose.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.*

/** Native vectors: incoming energy updates only this small drawing, not the sheet layout. */
@Composable
internal fun RecognitionWaveform(level: State<RecognitionAudioFrame>, active: Boolean, modifier: Modifier = Modifier) {
    val model = remember { RecognitionWaveModel() }
    val drawTick = remember { mutableIntStateOf(0) }
    val input by rememberUpdatedState(level)
    LaunchedEffect(active) {
        var previous = 0L
        var settling = 0f
        while (isActive && (active || settling < .6f)) {
            val update: (Long) -> Unit = { frame ->
                val dt = if (previous == 0L) 0f else (frame - previous) / 1_000_000_000f
                previous = frame
                model.advance(if (active) input.value else RecognitionAudioFrame.Silence, dt, active)
                if (!active) settling += dt
                drawTick.intValue++
            }
            if (active) withInfiniteAnimationFrameNanos(update) else withFrameNanos(update)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(144.dp).testTag("recognition-waveform")) {
        drawTick.intValue // Frame invalidation stays in the drawing phase.
        val width = minOf(size.width - 6.dp.toPx(), 286.dp.toPx()).coerceAtLeast(0f)
        val start = (size.width - width) / 2
        val step = width / (model.count - 1)
        val points = Array(8) { index ->
            val angle = index * 2f * PI.toFloat() / 8 - PI.toFloat() / 2
            val radius = model.cloudRadii[index]
            Offset(center.x + cos(angle) * width * .5f * radius, center.y + sin(angle) * size.height * .5f * radius)
        }
        val cloud = Path()
        cloud.moveTo(points[0].x, points[0].y)
        for (i in points.indices) {
            val previous = points[(i + points.size - 1) % points.size]
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val following = points[(i + 2) % points.size]
            val a = current + (next - previous) * .2f
            val b = next - (following - current) * .2f
            cloud.cubicTo(a.x, a.y, b.x, b.y, next.x, next.y)
        }
        cloud.close()
        drawPath(cloud, primary.copy(alpha = .12f))
        val stroke = 6.dp.toPx()
        for (i in model.bars.indices) {
            val x = start + i * step
            val height = model.bars[i] * size.height / 2
            if (height < stroke / 2) drawCircle(primary, stroke / 2f, Offset(x, center.y))
            else drawLine(primary, Offset(x, center.y - height), Offset(x, center.y + height),
                strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
