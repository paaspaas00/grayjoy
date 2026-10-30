package com.futo.platformplayer.compose.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.LibraryFilter
import com.futo.platformplayer.compose.ui.screens.watchLaterIcon
import kotlinx.coroutines.withTimeoutOrNull

@Stable
internal class NavigationShortcutState {
    var visible by mutableStateOf(false)
    var anchor by mutableStateOf<Rect?>(null)
    var onSelect: (LibraryFilter) -> Unit = {}
}

@Composable
internal fun NavigationShortcutOverlay(state: NavigationShortcutState, rootLeftPx: Float, modifier: Modifier = Modifier) {
    val transition = remember { MutableTransitionState(false) }; transition.targetState = state.visible
    PageBackHandler(enabled = state.visible, onBack = { state.visible = false })
    if (!transition.currentState && !transition.targetState) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null, onClick = { state.visible = false }).testTag("library-shortcuts-outside"))
        val widthPx = with(density) { 64.dp.toPx() }
        val x = ((state.anchor?.center?.x ?: constraints.maxWidth / 2f) - rootLeftPx - widthPx / 2)
            .coerceIn(0f, (constraints.maxWidth - widthPx).coerceAtLeast(0f))
        val originOffset = (state.anchor?.height ?: 0f) / 2 + with(density) { 12.dp.toPx() }
        AnimatedVisibility(visibleState = transition,
            enter = slideInVertically(tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it + originOffset.toInt() } + fadeIn(tween(140)),
            exit = slideOutVertically(tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it + originOffset.toInt() } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomStart).offset { IntOffset(x.toInt(), -with(density) { 12.dp.roundToPx() }) }) {
            LibraryShortcutButtons { state.onSelect(it); state.visible = false }
        }
    }
}

@Composable
private fun LibraryShortcutButtons(onSelect: (LibraryFilter) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp, modifier = Modifier.testTag("library-shortcuts")) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LibraryFilter.entries.forEach { filter ->
                IconButton(onClick = { onSelect(filter) }, modifier = Modifier.size(48.dp)
                    .testTag("library-shortcut-${filter.name.lowercase()}")) {
                    Icon(if (filter == LibraryFilter.WatchLater) watchLaterIcon() else filter.icon,
                        stringResource(filter.labelRes))
                }
            }
        }
    }
}

@Composable
internal fun Modifier.navigationLongPress(onLongPress: () -> Unit, onTap: () -> Unit): Modifier {
    val latestLong by rememberUpdatedState(onLongPress)
    val latestTap by rememberUpdatedState(onTap)
    return semantics {
    onLongClick { latestLong(); true }
}.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        down.consume()
        var cancelled = false
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            cancelled = up == null
            up?.consume()
            true
        }
        if (released == null) {
            latestLong()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        } else if (!cancelled) latestTap()
    }
}
}

@Composable
internal fun LibraryShortcutPopup(visible: Boolean, onDismiss: () -> Unit, onSelect: (LibraryFilter) -> Unit) {
    val transition = remember { MutableTransitionState(false) }; transition.targetState = visible
    if (!transition.currentState && !transition.targetState) return
    val heightPx = with(LocalDensity.current) { 232.dp.roundToPx() }
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -heightPx), onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(visibleState = transition,
            enter = fadeIn(tween(120)) + slideInVertically(tween(260)) { it },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(180)) { it }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 8.dp, modifier = Modifier.testTag("library-shortcuts")) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LibraryFilter.entries.forEach { filter ->
                        IconButton(onClick = { onSelect(filter); onDismiss() }, modifier = Modifier.size(48.dp)
                            .testTag("library-shortcut-${filter.name.lowercase()}")) {
                            Icon(if (filter == LibraryFilter.WatchLater) watchLaterIcon() else filter.icon,
                                stringResource(filter.labelRes))
                        }
                    }
                }
            }
        }
    }
}
