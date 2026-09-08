package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import kotlin.math.sin

@Composable
internal fun NowPlayingIndicator(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val phase = if (isPlaying) {
        rememberInfiniteTransition(label = "playing-bars").animateFloat(
            0f, 6.283185f,
            infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "playing-bars-phase",
        )
    } else rememberUpdatedState(0f)
    val label = stringResource(R.string.now_playing)
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.testTag("current-playlist-video").semantics { contentDescription = label },
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Canvas(Modifier.padding(5.dp).size(24.dp, 16.dp)) {
            repeat(3) { bar ->
                val fraction = if (isPlaying) 0.3f + 0.7f * ((sin(phase.value + bar * 2f) + 1f) / 2f) else 0.5f
                val height = size.height * fraction
                drawRoundRect(foreground, Offset(bar * size.width / 3f, size.height - height),
                    Size(size.width / 5f, height), CornerRadius(2.dp.toPx()))
            }
        }
    }
}
