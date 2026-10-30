package com.futo.platformplayer.compose.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.ui.GrayjayDestination
import com.futo.platformplayer.compose.ui.screens.LibraryFilter
import com.futo.platformplayer.compose.ui.screens.watchLaterIcon
import kotlin.math.*

private fun smoothArtwork(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}
private fun safePhase(value: Float) = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f

internal fun setupNormalizationMix(phase: Float): Float {
    val p = safePhase(phase)
    return minOf(smoothArtwork((p - .16f) / .3f), smoothArtwork((1f - p) / .13f))
}

internal data class SetupAutoplayFrame(val scene: Int, val slide: Float, val progress: Float)
internal fun setupAutoplayFrame(phase: Float): SetupAutoplayFrame {
    val p = safePhase(phase) % 1f
    val scene = if (p < .5f) 0 else 1
    val local = (p % .5f) * 2f
    val slide = smoothArtwork((local - .64f) / .26f)
    return SetupAutoplayFrame(scene, slide, (local / .6f).coerceIn(0f, 1f) * (1f - slide))
}

internal fun setupShortcutReveal(phase: Float): Float {
    val p = safePhase(phase)
    return minOf(smoothArtwork((p - .24f) / .15f), smoothArtwork((.98f - p) / .15f))
}

@Composable
internal fun AudioNormalizationArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clock = setupArtworkPhase(active, 6400, restPhase = 0f)
    val equalizer = rememberVectorPainter(Icons.Outlined.Tune)
    SetupDiagram("setup-normalization-art", 172f, modifier) {
        val phase = clock.value
        val mix = setupNormalizationMix((phase + .55f) % 1f)
        drawRoundRect(colors.surfaceContainerLow, Offset(0f, 4f), Size(360f, 164f), CornerRadius(28f))
        drawLine(colors.outlineVariant, Offset(130f, 86f), Offset(230f, 86f), 2f, StrokeCap.Round)
        for (x in listOf(8f, 218f)) {
            drawRoundRect(colors.surfaceContainerLowest, Offset(x, 20f), Size(134f, 132f), CornerRadius(20f))
            drawRoundRect(colors.outlineVariant.copy(alpha = .5f), Offset(x, 20f), Size(134f, 132f), CornerRadius(20f), style = Stroke(1f))
        }
        for (row in 0..1) {
            val ink = if (row == 0) colors.primary else colors.tertiary
            val input = if (row == 0) 26f else 8f
            val output = input + (17f - input) * mix
            val y = if (row == 0) 59f else 115f
            drawLine(ink.copy(alpha = .15f), Offset(232f, y - 17f), Offset(338f, y - 17f), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
            drawLine(ink.copy(alpha = .15f), Offset(232f, y + 17f), Offset(338f, y + 17f), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
            repeat(17) { i ->
                val shape = .25f + .75f * abs(sin(i * .8f + phase * 12f + row * 1.4f))
                val leftX = 27f + i * 5.7f
                val rightX = 237f + i * 5.7f
                drawLine(ink.copy(alpha = .7f), Offset(leftX, y - input * shape),
                    Offset(leftX, y + input * shape), 3.6f, StrokeCap.Round)
                drawLine(ink, Offset(rightX, y - output * shape),
                    Offset(rightX, y + output * shape), 3.6f, StrokeCap.Round)
            }
        }
        drawCircle(colors.surfaceContainerLow, 30f, Offset(180f, 86f))
        drawRoundRect(colors.primaryContainer, Offset(154f, 60f), Size(52f, 52f), CornerRadius(17f))
        drawSetupIcon(equalizer, Offset(180f, 86f), 28f, colors.onPrimaryContainer)
        drawLine(colors.primary, Offset(207f, 82f), Offset(213f, 86f), 2f, StrokeCap.Round)
        drawLine(colors.primary, Offset(213f, 86f), Offset(207f, 90f), 2f, StrokeCap.Round)
        if (mix > 0f) {
            drawCircle(colors.primary.copy(alpha = mix), 10f, Offset(336f, 24f))
            drawLine(colors.onPrimary.copy(alpha = mix), Offset(332f, 24f), Offset(335f, 27f), 1.8f, StrokeCap.Round)
            drawLine(colors.onPrimary.copy(alpha = mix), Offset(335f, 27f), Offset(341f, 21f), 1.8f, StrokeCap.Round)
        }
    }
}

@Composable
internal fun YoutubeAccountArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clock = setupArtworkPhase(active, 5200, restPhase = 0f)
    val link = rememberVectorPainter(Icons.Outlined.Link)
    val person = rememberVectorPainter(Icons.Outlined.Person)
    SetupDiagram("setup-youtube-account-art", 160f, modifier) {
        val p = clock.value
        val connection = smoothArtwork((p - .14f) / .4f) * smoothArtwork((1f - p) / .15f)
        drawRoundRect(colors.surfaceContainerLow, Offset(0f, 4f), Size(360f, 152f), CornerRadius(28f))
        drawCircle(colors.primary.copy(alpha = .05f), 65f, Offset(83f, 80f))
        drawCircle(colors.tertiary.copy(alpha = .06f), 58f, Offset(279f, 80f))
        drawLine(colors.outlineVariant, Offset(114f, 80f), Offset(250f, 80f), 2f, StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f)))
        drawLine(colors.primary.copy(alpha = .45f), Offset(114f, 80f),
            Offset(114f + 136f * connection, 80f), 2f, StrokeCap.Round)
        drawRoundRect(colors.surfaceContainerLowest, Offset(37f, 39f), Size(91f, 82f), CornerRadius(24f))
        drawRoundRect(Color(0xFFFF0000), Offset(56f, 59f), Size(53f, 38f), CornerRadius(11f))
        drawPath(Path().apply { moveTo(77f, 68f); lineTo(93f, 78f); lineTo(77f, 88f); close() }, Color.White)
        drawCircle(colors.primaryContainer, 37f, Offset(279f, 80f))
        drawSetupIcon(person, Offset(279f, 80f), 39f, colors.onPrimaryContainer)
        drawCircle(colors.surfaceContainerLow, 25f, Offset(183f, 80f))
        drawSetupIcon(link, Offset(183f, 80f), 30f, colors.primary)
        if (connection > .01f) {
            val check = smoothArtwork((connection - .7f) / .3f)
            drawCircle(colors.primary.copy(alpha = check), 12f, Offset(307f, 105f))
            drawLine(colors.onPrimary.copy(alpha = check), Offset(302f, 105f), Offset(306f, 109f), 2f, StrokeCap.Round)
            drawLine(colors.onPrimary.copy(alpha = check), Offset(306f, 109f), Offset(313f, 101f), 2f, StrokeCap.Round)
        }
    }
}

@Composable
internal fun YoutubeAutoplayArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clock = setupArtworkPhase(active, 9600, restPhase = 0f)
    val play = rememberVectorPainter(Icons.Outlined.PlayArrow)
    val next = rememberVectorPainter(Icons.Outlined.SkipNext)
    val screen = remember { Path() }
    SetupDiagram("setup-youtube-autoplay-art", 190f, modifier) {
        val frame = setupAutoplayFrame(clock.value)
        drawRoundRect(colors.surfaceContainerLow, Offset(0f, 4f), Size(360f, 182f), CornerRadius(28f))
        val rect = Rect(26f, 20f, 334f, 158f)
        screen.reset(); screen.addRoundRect(RoundRect(rect, CornerRadius(20f)))
        clipPath(screen) {
            repeat(2) { index ->
                val alternate = (frame.scene + index) % 2 == 1
                val x = rect.left + (index - frame.slide) * rect.width
                val fill = if (alternate) colors.tertiaryContainer else colors.primaryContainer
                val ink = if (alternate) colors.onTertiaryContainer else colors.onPrimaryContainer
                drawRect(fill, Offset(x, rect.top), rect.size)
                drawCircle(ink.copy(alpha = .10f), 43f, Offset(x + 232f, 55f))
                drawPath(Path().apply {
                    moveTo(x, 145f); lineTo(x + 71f, if (alternate) 83f else 65f)
                    lineTo(x + 151f, 140f); lineTo(x + 212f, 96f)
                    lineTo(x + 308f, 145f); close()
                }, ink.copy(alpha = .12f))
                drawCircle(ink.copy(alpha = .08f), 24f, Offset(x + 154f, 84f))
                drawSetupIcon(play, Offset(x + 154f, 84f), 32f, ink)
            }
            drawLine(colors.onSurface.copy(alpha = .15f), Offset(44f, 141f), Offset(316f, 141f), 3f, StrokeCap.Round)
            drawLine(colors.primary, Offset(44f, 141f), Offset(44f + 272f * frame.progress, 141f), 3f, StrokeCap.Round)
            val countdown = ((frame.progress - .6f) / .4f).coerceIn(0f, 1f)
            if (countdown > 0f) {
                drawCircle(colors.surfaceContainerLowest, 19f, Offset(304f, 113f))
                drawArc(colors.primary, -90f, 360f * countdown, false, Offset(285f, 94f), Size(38f, 38f), style = Stroke(2f))
                drawSetupIcon(next, Offset(304f, 113f), 23f, colors.primary)
            }
        }
        repeat(2) { index ->
            val selected = if (frame.slide < .5f) frame.scene else 1 - frame.scene
            drawRoundRect(if (selected == index) colors.primary else colors.outlineVariant,
                Offset(164f + index * 20f, 171f), Size(if (selected == index) 14f else 6f, 5f), CornerRadius(3f))
        }
    }
}

@Composable
internal fun LibraryShortcutsArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clock = setupArtworkPhase(active, 5600, restPhase = 0f)
    val destinations = listOf(GrayjayDestination.Home, GrayjayDestination.Subscriptions,
        GrayjayDestination.Search, GrayjayDestination.Library, GrayjayDestination.Settings)
    val nav = destinations.map { rememberVectorPainter(it.icon) }
    val shortcuts = LibraryFilter.entries.map {
        rememberVectorPainter(if (it == LibraryFilter.WatchLater) watchLaterIcon() else it.icon)
    }
    val touch = rememberVectorPainter(Icons.Outlined.TouchApp)
    SetupDiagram("setup-library-shortcuts-art", 228f, modifier) {
        val p = clock.value
        val reveal = setupShortcutReveal(p)
        val pressing = minOf(smoothArtwork(p / .08f), smoothArtwork((.45f - p) / .08f))
        drawRoundRect(colors.surfaceContainerLow, Offset(0f, 4f), Size(360f, 220f), CornerRadius(28f))
        // The panel is painted first and clipped above the navbar, just like the real overlay.
        clipRect(0f, 4f, 360f, 174f) {
            val top = 190f - 146f * reveal
            drawRoundRect(Color.Black.copy(alpha = .06f * reveal), Offset(228f, top + 3f),
                Size(44f, 124f), CornerRadius(22f))
            drawRoundRect(colors.secondaryContainer, Offset(226f, top), Size(44f, 124f), CornerRadius(22f))
            shortcuts.forEachIndexed { index, icon ->
                drawSetupIcon(icon, Offset(248f, top + 20f + index * 28f), 20f, colors.onSecondaryContainer)
            }
        }
        drawRoundRect(colors.surfaceContainerHigh, Offset(8f, 174f), Size(344f, 46f), CornerRadius(20f))
        drawRoundRect(colors.secondaryContainer, Offset(226f, 180f), Size(44f, 32f), CornerRadius(16f))
        nav.forEachIndexed { index, icon ->
            drawSetupIcon(icon, Offset(44f + index * 68f, 196f), 22f,
                if (index == 3) colors.onSecondaryContainer else colors.onSurfaceVariant)
        }
        if (pressing > 0f) {
            drawCircle(colors.primary.copy(alpha = .13f * pressing), 22f, Offset(248f, 196f))
            drawArc(colors.primary.copy(alpha = pressing), -90f, 360f * (p / .24f).coerceIn(0f, 1f), false,
                Offset(227f, 175f), Size(42f, 42f), style = Stroke(2f))
            drawSetupIcon(touch, Offset(260f, 206f), 38f, colors.inverseSurface.copy(alpha = pressing))
        }
    }
}

@Composable
private fun SetupDiagram(tag: String, height: Float, modifier: Modifier, draw: DrawScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.widthIn(max = 338.dp).fillMaxWidth().aspectRatio(360f / height).testTag(tag)) {
            val factor = size.width / 360f
            withTransform({ scale(factor, factor, Offset.Zero) }) { draw() }
        }
    }
}

private fun DrawScope.drawSetupIcon(painter: Painter, center: Offset, size: Float, color: Color) {
    // VectorPainter caches at its requested pixel size. Render at the final density, rather
    // than enlarging a tiny cache with the diagram's surrounding canvas transform.
    val factor = (this.size.width / 360f).coerceAtLeast(.01f)
    withTransform({
        translate(center.x - size / 2, center.y - size / 2)
        scale(1f / factor, 1f / factor, Offset.Zero)
    }) {
        with(painter) { draw(Size(size * factor, size * factor), colorFilter = ColorFilter.tint(color)) }
    }
}
