package com.futo.platformplayer.compose.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Stable
internal class NowPlayingBackState {
    var active by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var fromRight by mutableStateOf(false); private set
    var target by mutableStateOf<BrowsePage?>(null); private set
    var previewFrame by mutableStateOf<android.graphics.Bitmap?>(null); private set
    private var generation = 0L
    fun begin(page: BrowsePage): Long {
        generation++; target = page; progress = 0f; fromRight = false; previewFrame = null; active = true
        return generation
    }
    fun isCurrent(token: Long) = active && generation == token
    fun move(token: Long, value: Float, rightEdge: Boolean = fromRight) {
        if (isCurrent(token)) { progress = normalizedBackProgress(value); fromRight = rightEdge }
    }
    fun clear(token: Long) { if (isCurrent(token)) reset() }
    fun frame(token: Long, bitmap: android.graphics.Bitmap?) { if (isCurrent(token)) previewFrame = bitmap }
    fun reset() { generation++; active = false; progress = 0f; target = null; previewFrame = null }
}

private fun normalizedBackProgress(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
internal fun backDepartureScale(progress: Float) = 1f - .08f * normalizedBackProgress(progress)
internal fun backDepartureAlpha(progress: Float) = 1f - ((normalizedBackProgress(progress) - .65f) / .35f).coerceIn(0f, 1f)

internal fun Modifier.nowPlayingBackDeparture(state: NowPlayingBackState): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(.5f, .5f)
    scaleX = 1f; scaleY = 1f; translationX = 0f; alpha = 1f; clip = false; shadowElevation = 0f
    if (state.active) {
        val p = state.progress
        transformOrigin = TransformOrigin(.5f, .5f)
        scaleX = backDepartureScale(p); scaleY = scaleX
        translationX = size.width * .22f * p * if (state.fromRight) -1f else 1f
        alpha = backDepartureAlpha(p)
        shape = RoundedCornerShape((24f * p).dp); clip = p > 0f
        shadowElevation = 8.dp.toPx() * p
    }
}
