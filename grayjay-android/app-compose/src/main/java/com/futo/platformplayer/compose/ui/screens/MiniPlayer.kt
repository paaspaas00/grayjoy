package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.VideoUiModel

@Composable
fun MiniPlayer(
    video: VideoUiModel,
    isPlaying: Boolean,
    progress: Float,
    canSkip: Boolean,
    chromeAlpha: Float,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onClose: () -> Unit,
    onVideoBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedAlpha = chromeAlpha.coerceIn(0f, 1f)
    Surface(
        onClick = onExpand,
        enabled = normalizedAlpha > 0.95f,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = normalizedAlpha }
            .then(
                if (normalizedAlpha <= 0.001f) Modifier.clearAndSetSemantics { }
                else Modifier.testTag("mini-player"),
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        MiniPlayerChrome(
            video = video,
            isPlaying = isPlaying,
            progress = progress,
            canSkip = canSkip,
            onTogglePlayback = onTogglePlayback,
            onSkipToNext = onSkipToNext,
            onClose = onClose,
            onVideoBoundsChanged = onVideoBoundsChanged,
        )
    }
}

@Composable
internal fun MiniPlayerChrome(
    video: VideoUiModel,
    isPlaying: Boolean,
    progress: Float,
    canSkip: Boolean,
    onTogglePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onVideoBoundsChanged: ((Rect) -> Unit)? = null,
    applyTestTags: Boolean = true,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .then(
                        if (onVideoBoundsChanged != null) {
                            Modifier.onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                onVideoBoundsChanged(
                                    Rect(
                                        left = position.x,
                                        top = position.y,
                                        right = position.x + coordinates.size.width,
                                        bottom = position.y + coordinates.size.height,
                                    ),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (video.playbackAudioOnly) {
                    Column(
                        modifier = Modifier.testTag("mini-player-audio-only"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.mini_player_audio),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    video.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    video.creator,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = onTogglePlayback,
                modifier = if (applyTestTags) Modifier.testTag("mini-player-toggle") else Modifier,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.pause_mini_player else R.string.play_mini_player,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (canSkip) {
                IconButton(
                    onClick = onSkipToNext,
                    modifier = if (applyTestTags) Modifier.testTag("mini-player-next") else Modifier,
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.next_in_queue),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = if (applyTestTags) Modifier.testTag("mini-player-close") else Modifier,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close_mini_player),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        )
    }
}
