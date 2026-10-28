package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockSegment
import kotlin.math.roundToInt

internal val PlayerSeekHandleWidth = 4.dp
internal val PlayerSeekHandleHeight = 32.dp

/** Drawn inside Slider's track slot, using the same width and rounding as its thumb placement. */
@Composable
internal fun PlayerSeekTrack(
    progress: Float,
    bufferedProgress: Float,
    durationMs: Long,
    segments: List<SponsorBlockSegment>,
    progressColor: Color,
    modifier: Modifier = Modifier,
    unplayedColor: Color = Color.White.copy(alpha = .22f),
    bufferedColor: Color = Color.White.copy(alpha = .52f),
) {
    val outline = remember { Path() }
    Canvas(modifier.fillMaxWidth().height(12.dp).testTag("player-buffered-track")) {
        val thumb = (size.width * progress.coerceIn(0f, 1f)).roundToInt().toFloat()
        val gap = PlayerSeekHandleWidth.toPx() / 2f + 4.dp.toPx()
        val leftEnd = (thumb - gap).coerceIn(0f, size.width)
        val rightStart = (thumb + gap).coerceIn(0f, size.width)
        val outer = CornerRadius(size.height / 2f)
        val inner = CornerRadius(2.dp.toPx())
        outline.reset()
        if (leftEnd > 0f) outline.addRoundRect(RoundRect(
            left = 0f, top = 0f, right = leftEnd, bottom = size.height,
            topLeftCornerRadius = outer, topRightCornerRadius = inner,
            bottomRightCornerRadius = inner, bottomLeftCornerRadius = outer,
        ))
        if (rightStart < size.width) outline.addRoundRect(RoundRect(
            left = rightStart, top = 0f, right = size.width, bottom = size.height,
            topLeftCornerRadius = inner, topRightCornerRadius = outer,
            bottomRightCornerRadius = outer, bottomLeftCornerRadius = inner,
        ))
        val isRtl = layoutDirection == LayoutDirection.Rtl
        withTransform({
            if (isRtl) scale(-1f, 1f, center)
        }) {
            clipPath(outline) {
                drawRect(unplayedColor)
                drawRect(bufferedColor,
                    size = Size(size.width * bufferedProgress.coerceIn(0f, 1f), size.height))
                drawRect(progressColor, size = Size(thumb, size.height))
                if (durationMs > 0L) segments.forEach { segment ->
                    val start = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val end = (segment.endMs.toFloat() / durationMs).coerceIn(start, 1f)
                    drawRect(Color(0xFF8FE388), topLeft = Offset(size.width * start, 0f),
                        size = Size(size.width * (end - start), size.height))
                }
                if (size.width - rightStart > size.height) {
                    drawCircle(progressColor, radius = 2.dp.toPx(),
                        center = Offset(size.width - size.height / 2f, size.height / 2f))
                }
            }
        }
    }
}
