package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.VideoUiModel
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MiniPlayer(
    video: VideoUiModel,
    isPlaying: Boolean,
    progress: Float,
    player: Player? = null,
    canSkip: Boolean,
    chromeAlpha: Float,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onClose: () -> Unit,
    onVideoBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
) {
    val normalizedAlpha = chromeAlpha.coerceIn(0f, 1f)
    val expandLabel = stringResource(R.string.open_item, stringResource(R.string.now_playing))
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (normalizedAlpha > .95f && interactionEnabled) Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(expandLabel) { onExpand(); true })
            } else Modifier)
            .then(
                if (normalizedAlpha >= 0.999f) Modifier
                else Modifier.graphicsLayer { alpha = normalizedAlpha },
            )
            .then(
                if (normalizedAlpha <= 0.001f) Modifier.clearAndSetSemantics { }
                else Modifier.testTag("mini-player"),
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        MiniPlayerChrome(
            video = video,
            isPlaying = isPlaying,
            progress = progress,
            player = player.takeIf { normalizedAlpha > 0.001f },
            controlsEnabled = normalizedAlpha > 0.95f && interactionEnabled,
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
    player: Player? = null,
    canSkip: Boolean,
    onTogglePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onVideoBoundsChanged: ((Rect) -> Unit)? = null,
    applyTestTags: Boolean = true,
    controlsEnabled: Boolean = true,
    showArtwork: Boolean = false,
    previewFrame: android.graphics.Bitmap? = null,
) {
    val displayedProgress by produceState(
        initialValue = progress.coerceIn(0f, 1f),
        key1 = player,
        key2 = video.id,
        key3 = if (player == null) progress else isPlaying,
    ) {
        if (player == null) {
            value = progress.coerceIn(0f, 1f)
            return@produceState
        }
        while (isActive) {
            val duration = player.duration
            value = if (duration > 0L) {
                (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                progress.coerceIn(0f, 1f)
            }
            delay(if (isPlaying) 500L else 1_500L)
        }
    }
    val leftHanded = com.futo.platformplayer.compose.ui.LocalInteractionControls.current.leftHanded
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    val artwork: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .width(if (LocalMusicPresentation.current) { if (narrow) 64.dp else 76.dp }
                        else if (narrow) 84.dp else 112.dp)
                    .height(70.dp)
                    .then(if (applyTestTags) Modifier.testTag("mini-player-artwork") else Modifier)
                    .background(if (LocalMusicPresentation.current) androidx.compose.ui.graphics.Color.Transparent
                        else MaterialTheme.colorScheme.surfaceContainerLowest)
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
                if (LocalMusicPresentation.current) {
                    MusicArtwork(video.thumbnailUrl, Modifier.size(56.dp))
                } else if (video.playbackAudioOnly) {
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
                } else if (previewFrame != null) {
                    Image(previewFrame.asImageBitmap(), null, Modifier.fillMaxWidth().height(70.dp), contentScale = ContentScale.Fit)
                } else if (showArtwork && video.thumbnailUrl.isNotBlank()) {
                    RemoteBitmapImage(video.thumbnailUrl, MaterialTheme.colorScheme.surfaceContainerLowest,
                        Modifier.fillMaxWidth().height(70.dp), requestHeaders = video.thumbnailRequestHeaders,
                        cropToBounds = true, requestSize = androidx.compose.ui.unit.IntSize(320, 180))
                }
            }
    }
    val title: @Composable RowScope.() -> Unit = {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (applyTestTags) Modifier.testTag("mini-player-title") else Modifier)
                    .padding(horizontal = if (narrow) 6.dp else 12.dp),
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
    }
    val play: @Composable () -> Unit = {
            IconButton(
                onClick = onTogglePlayback,
                enabled = controlsEnabled,
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
    }
    val next: @Composable () -> Unit = {
            if (canSkip) {
                IconButton(
                    onClick = onSkipToNext,
                    enabled = controlsEnabled,
                    modifier = if (applyTestTags) Modifier.testTag("mini-player-next") else Modifier,
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.next_in_queue),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
    }
    val close: @Composable () -> Unit = {
            IconButton(
                onClick = onClose,
                enabled = controlsEnabled,
                modifier = if (applyTestTags) Modifier.testTag("mini-player-close") else Modifier,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close_mini_player),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(70.dp)
            .padding(start = if (leftHanded) { if (narrow) 4.dp else 10.dp } else 0.dp,
                end = if (leftHanded) 0.dp else if (narrow) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (leftHanded) { close(); play(); next(); title(); artwork() }
            else { artwork(); title(); play(); next(); close() }
        }
        LinearProgressIndicator(
            progress = { displayedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .then(if (applyTestTags) Modifier.testTag("mini-player-progress") else Modifier),
        )
    }
}
