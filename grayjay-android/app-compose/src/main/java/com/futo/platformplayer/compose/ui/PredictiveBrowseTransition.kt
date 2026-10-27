package com.futo.platformplayer.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal val LocalNavigationPreview = staticCompositionLocalOf { false }

internal data class BrowsePage(
    val route: BrowseRoute,
    val channel: ChannelUiModel? = null,
    val playlist: PlaylistUiModel? = null,
    val showBack: Boolean = false,
) {
    val key: String get() = when {
        route.channelId != null -> "channel:${route.channelId}"
        route.playlistId != null -> "playlist:${route.playlistId}"
        else -> "destination:${route.destination}"
    }
}

/** Both pages stay composed until commit. Cancelling never mutates the navigation stack. */
@Composable
internal fun PredictiveBrowseTransition(
    current: BrowsePage,
    previous: BrowsePage?,
    progress: State<Float>,
    fromRight: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (BrowsePage, Boolean) -> Unit,
) {
    val target = previous?.takeIf { it.key != current.key }
    val pages = if (target == null) listOf(current) else listOf(target, current)
    Box(modifier.clipToBounds()) {
        for (page in pages) key(page.key) {
            val preview = page.key != current.key
            val surface = MaterialTheme.colorScheme.background
            Box(Modifier.fillMaxSize().graphicsLayer {
                val p = if (target == null) 0f else progress.value.coerceIn(0f, 1f)
                val direction = if (fromRight) -1f else 1f
                translationX = size.width * direction * if (preview) -.06f * (1f - p) else .22f * p
                // Keep the departing page opaque while tracking the finger. Fading both sets
                // of text during the whole gesture makes them overlap and look like a ghost.
                alpha = if (preview) .85f + .15f * p else 1f - ((p - .65f) / .35f).coerceIn(0f, 1f)
                scaleX = if (preview) .98f + .02f * p else 1f - .08f * p
                scaleY = scaleX
                if (!preview && p > 0f) {
                    shape = RoundedCornerShape(24.dp * p)
                    clip = true
                    shadowElevation = 8.dp.toPx() * p
                } else {
                    clip = false
                    shadowElevation = 0f
                }
            }.background(surface).testTag(if (preview) "back-preview-page" else "browse-current-page")
                .then(if (preview) Modifier.clearAndSetSemantics { } else Modifier)) {
                CompositionLocalProvider(LocalNavigationPreview provides preview,
                    LocalPageBackEnabled provides (!preview && target == null)) { content(page, preview) }
            }
        }
        if (target != null) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
    }
}
