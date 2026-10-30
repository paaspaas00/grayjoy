package com.futo.platformplayer.compose.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.ui.ProfileProtection
import com.futo.platformplayer.compose.ui.ProfileSourceIcon
import com.futo.platformplayer.compose.ui.ProfileUiModel
import kotlin.math.*

private fun artworkSmoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun setupProfileSeparation(phase: Float): Float {
    val t = phase.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return minOf(artworkSmoothStep(t / .20f), artworkSmoothStep((1f - t) / .16f))
}

@Composable
internal fun SourceProfilesArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val clock = setupArtworkPhase(active, 6400)
    val profiles = remember {
        listOf(ProfileUiModel("demo-video", "YouTube", ProfileProtection.None, sourceId = "youtube"),
            ProfileUiModel("demo-music", "Spotify", ProfileProtection.None, sourceId = "spotify"))
    }
    Box(modifier.fillMaxWidth().testTag("setup-source-profiles-art"), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 338.dp).fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            profiles.forEachIndexed { index, profile ->
                val colors = MaterialTheme.colorScheme
                val accent = if (index == 0) Color(0xFFAF4343) else Color(0xFF268D5A)
                Surface(Modifier.weight(1f).graphicsLayer {
                    val progress = setupProfileSeparation((clock.value + .2f) % 1f)
                    translationX = (if (index == 0) 1 else -1) * (size.width / 2 + 10.dp.toPx()) * (1f - progress)
                    rotationZ = (if (index == 0) -7f else 7f) * (1f - progress)
                }, shape = RoundedCornerShape(24.dp),
                    color = lerp(colors.surfaceContainerLow, accent, .09f),
                    border = BorderStroke(1.dp, lerp(colors.outlineVariant, accent, .35f)),
                    shadowElevation = 1.dp) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(64.dp)) {
                            Surface(Modifier.align(Alignment.Center).size(52.dp), shape = CircleShape,
                                color = lerp(colors.surfaceContainerHigh, accent, .18f)) {
                                Icon(Icons.Outlined.Person, null, Modifier.padding(10.dp),
                                    tint = colors.onSurface)
                            }
                            Surface(Modifier.align(Alignment.BottomEnd).size(27.dp), shape = CircleShape,
                                color = colors.surface, border = BorderStroke(2.dp, colors.surface)) {
                                ProfileSourceIcon(profile, Modifier.padding(4.dp))
                            }
                        }
                        Text(profile.name, style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface, maxLines = 1)
                        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                            val left = size.width * .1f
                            drawLine(accent.copy(alpha = .55f), Offset(left, 3.dp.toPx()),
                                Offset(size.width * .9f, 3.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                            drawLine(colors.onSurfaceVariant.copy(alpha = .22f), Offset(left, 12.dp.toPx()),
                                Offset(size.width * .65f, 12.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}

internal data class SetupBugFrame(val position: Float, val heading: Float, val walking: Boolean)

internal fun setupBugFrame(phase: Float): SetupBugFrame {
    val t = phase.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return when {
        t < .42f -> SetupBugFrame(artworkSmoothStep(t / .42f), 0f, true)
        t < .50f -> SetupBugFrame(1f, 180f * artworkSmoothStep((t - .42f) / .08f), false)
        t < .92f -> SetupBugFrame(1f - artworkSmoothStep((t - .50f) / .42f), 180f, true)
        else -> SetupBugFrame(0f, 180f + 180f * artworkSmoothStep((t - .92f) / .08f), false)
    }
}

@Composable
internal fun CrashLogBugArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val phase = setupArtworkPhase(active, 9000, restPhase = 0f)
    Canvas(modifier.fillMaxWidth().height(76.dp).testTag("setup-crash-bug-art")) {
        val frame = setupBugFrame(phase.value)
        val scale = minOf(1.dp.toPx(), size.width / 130f)
        val margin = 36f * scale
        val track = (size.width - 2 * margin).coerceAtLeast(0f)
        val centerY = size.height / 2
        val x = margin + track * frame.position
        drawRoundRect(colors.surfaceContainerLow, size = size, cornerRadius = CornerRadius(22.dp.toPx()))
        drawLine(colors.outlineVariant.copy(alpha = .65f), Offset(margin, centerY),
            Offset(size.width - margin, centerY), 1.dp.toPx(), StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 7.dp.toPx())))
        for (end in listOf(margin, size.width - margin))
            drawCircle(colors.primary.copy(alpha = .16f), 3.dp.toPx(), Offset(end, centerY))
        val step = if (frame.walking && active) sin(phase.value * PI.toFloat() * 56f) else 0f
        withTransform({
            translate(x, centerY)
            rotate(frame.heading, Offset.Zero)
            scale(scale, scale, Offset.Zero)
        }) {
            // Three articulated pairs alternate their stride while the body stays on the path.
            for (row in -1..1) for (side in listOf(-1f, 1f)) {
                val stride = step * (if (row == 0) -1f else 1f) * side
                val root = Offset(row * 8f, side * 7f)
                val knee = Offset(row * 10f + stride * 3f, side * 15f)
                val foot = Offset(row * 13f - stride * 4f, side * 21f)
                drawLine(colors.primary, root, knee, 2.5f, StrokeCap.Round)
                drawLine(colors.primary, knee, foot, 2.5f, StrokeCap.Round)
            }
            for (side in listOf(-1f, 1f)) {
                drawLine(colors.primary, Offset(18f, side * 3f), Offset(24f, side * 5f), 2f, StrokeCap.Round)
                drawLine(colors.primary, Offset(24f, side * 5f), Offset(28f, side * 11f), 2f, StrokeCap.Round)
            }
            drawOval(colors.primary, Offset(-16f, -11f), Size(32f, 22f))
            drawCircle(colors.primary, 7f, Offset(16f, 0f))
            drawLine(colors.onPrimary.copy(alpha = .75f), Offset(-11f, 0f), Offset(8f, 0f), 1.5f, StrokeCap.Round)
            drawCircle(colors.onPrimary, 1.3f, Offset(19f, -3f))
            drawCircle(colors.onPrimary, 1.3f, Offset(19f, 3f))
        }
    }
}
