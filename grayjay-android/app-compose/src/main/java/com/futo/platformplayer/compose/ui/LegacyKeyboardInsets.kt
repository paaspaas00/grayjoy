package com.futo.platformplayer.compose.ui

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

internal data class LegacyKeyboardInsets(val visible: Boolean = false, val occlusion: Int = 0)

internal fun legacyKeyboardInsets(rootBottom: Int, contentBottom: Int, visibleBottom: Int, threshold: Int): LegacyKeyboardInsets {
    val visible = rootBottom - visibleBottom > threshold
    return LegacyKeyboardInsets(visible, if (visible) (contentBottom - visibleBottom).coerceAtLeast(0) else 0)
}

/** Older OEMs report IME insets to DecorView but not Compose, or resize only part of the window. */
@Composable
internal fun rememberLegacyKeyboardInsets(): State<LegacyKeyboardInsets> {
    val view = LocalView.current
    val state = remember(view) { mutableStateOf(LegacyKeyboardInsets()) }
    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= 30) onDispose { } else {
            val frame = Rect()
            val rootPosition = IntArray(2)
            val contentPosition = IntArray(2)
            val threshold = (100 * view.resources.displayMetrics.density).toInt()
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                view.getWindowVisibleDisplayFrame(frame)
                view.rootView.getLocationOnScreen(rootPosition)
                view.getLocationOnScreen(contentPosition)
                state.value = legacyKeyboardInsets(rootPosition[1] + view.rootView.height,
                    contentPosition[1] + view.height, frame.bottom, threshold)
            }
            view.viewTreeObserver.addOnGlobalLayoutListener(listener)
            view.post { if (view.isAttachedToWindow) listener.onGlobalLayout() }
            onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
    }
    return state
}
