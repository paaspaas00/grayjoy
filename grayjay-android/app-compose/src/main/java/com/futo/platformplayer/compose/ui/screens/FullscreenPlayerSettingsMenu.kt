package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.futo.platformplayer.compose.ui.PageBackHandler
import kotlin.math.roundToInt

/** In-window overlay: opening settings must not reveal system bars or replace the video surface. */
@Composable
internal fun FullscreenPlayerSettingsMenu(
    visible: Boolean,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    PageBackHandler(enabled = visible, onBack = onBack)
    if ((!visibility.currentState && !visibility.targetState) || anchorBounds == null) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(30f)) {
        val margin = 12.dp
        val menuWidth = minOf(360.dp, (maxWidth - margin * 2).coerceAtLeast(1.dp))
        val marginPx = with(density) { margin.toPx() }
        val widthPx = with(density) { menuWidth.toPx() }
        val x = (anchorBounds.right - widthPx).coerceIn(marginPx,
            (constraints.maxWidth - widthPx - marginPx).coerceAtLeast(marginPx))
        val y = anchorBounds.top.coerceIn(marginPx,
            (constraints.maxHeight - marginPx).coerceAtLeast(marginPx))
        val menuHeight = with(density) { (constraints.maxHeight - y - marginPx).coerceAtLeast(1f).toDp() }
        Box(Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
        ).testTag("fullscreen-settings-outside"))
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(90)), exit = fadeOut(tween(90)),
            modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        ) {
            Surface(
                modifier = Modifier.width(menuWidth).heightIn(max = menuHeight)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .testTag("fullscreen-settings-menu"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .97f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
                content = content,
            )
        }
    }
}
