package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.helpAnchor
import com.futo.platformplayer.compose.ui.*

internal val LocalMusicPresentation = staticCompositionLocalOf { false }
internal val LocalMusicPlayerDrag = staticCompositionLocalOf<Modifier> { Modifier }

@Composable
internal fun MusicArtwork(url: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) RemoteBitmapImage(url = url, modifier = Modifier.fillMaxSize(),
            placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh, showParentPlaceholderWhileLoading = true,
            requestSize = if (large) androidx.compose.ui.unit.IntSize(768, 768) else null)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MusicTrackRow(
    video: VideoUiModel,
    index: Int,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    artwork: Boolean = false,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .heightIn(min = 68.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (artwork) Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            MusicArtwork(video.thumbnailUrl, Modifier.fillMaxSize())
            if (current) Icon(if (playing) Icons.Outlined.GraphicEq else Icons.Outlined.Pause, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .8f), MaterialTheme.shapes.small))
        } else Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (current) Icon(if (playing) Icons.Outlined.GraphicEq else Icons.Outlined.Pause, null,
                tint = MaterialTheme.colorScheme.primary)
            else Text("${index + 1}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(listOf(video.creator, video.duration).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else IconButton(onClick = onMore ?: onLongClick) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.details))
        }
    }
}

@Composable
internal fun MusicPlaylistHeader(playlist: PlaylistUiModel, videos: List<VideoUiModel>,
    onPlay: () -> Unit, onDownload: () -> Unit, onMore: () -> Unit, isBusy: Boolean = false, downloadActive: Boolean = false) {
    val duration = remember(videos) { musicDuration(videos) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MusicArtwork(playlist.thumbnailUrl.ifBlank { videos.firstOrNull()?.thumbnailUrl.orEmpty() },
                Modifier.weight(.43f).aspectRatio(1f))
            Column(Modifier.weight(.57f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(playlist.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (playlist.description.isNotBlank()) Text(playlist.description, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (playlist.videoCount > videos.size) playlist.videoCount.toString() else "${videos.size} · $duration",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    IconButton(onClick = onDownload, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("music-playlist-download").helpAnchor("playlist-download-audio")) {
                        if (downloadActive) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Download, stringResource(R.string.download_all_audio))
                    }
                    IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.details)) }
                }
            }
        }
        AdaptiveButton(onClick = onPlay, enabled = videos.isNotEmpty() && !isBusy, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("music-playlist-play").helpAnchor("playlist-play-all")) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.play_all))
        }
    }
}

internal fun musicDuration(videos: List<VideoUiModel>): String {
    val seconds = videos.sumOf { video ->
        val parts = video.duration.split(':').map { it.toLongOrNull() }
        if (parts.size !in 1..3 || parts.any { it == null || it < 0 || it > 86_400 }) 0L
        else parts.fold(0L) { value, part -> value * 60 + part!! }
    }
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MusicNowPlaying(
    video: VideoUiModel, player: Player, playback: PlaybackUiState, nowPlaying: NowPlayingUiState,
    queue: List<VideoUiModel>, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Float) -> Unit, onWatchLater: () -> Unit, onDownload: () -> Unit, onAddToPlaylist: () -> Unit,
    onRetry: () -> Unit, onPlay: (VideoUiModel) -> Unit, onMore: (VideoUiModel) -> Unit,
    onSpeed: (Float) -> Unit, onArtworkBounds: (Rect) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    fullscreen: Boolean = false,
    onFullscreen: (() -> Unit)? = null,
) {
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val queueActions = LocalTemporaryQueue.current
    val currentIndex = playback.queueVideoIds.indexOf(video.id)
    val timelineVideo = remember(video) { video.copy(storyboard = null) }
    BoxWithConstraints(Modifier.fillMaxSize().then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier).testTag("music-now-playing")) {
        val landscape = maxWidth > maxHeight
        val coverSize = minOf(if (landscape) maxWidth * .42f else maxWidth - 48.dp,
            maxHeight * if (landscape) .8f else .49f).coerceAtLeast(120.dp)
        val art: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fullscreen) Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.titleMedium)
                MusicArtwork(video.thumbnailUrl, Modifier.size(coverSize).then(if (fullscreen) Modifier else LocalMusicPlayerDrag.current)
                    .onGloballyPositioned { if (!fullscreen) onArtworkBounds(it.boundsInRoot()) }.testTag("music-player-artwork"), large = true)
            }
        }
        val controls: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(video.title, style = MaterialTheme.typography.headlineSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.creator, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                PlayerTimelineRow(video = timelineVideo, player = player,
                    remotePositionMs = playback.positionMs.takeIf { playback.isCasting }, durationMs = playback.durationMs,
                    bufferedPercentage = playback.bufferedPercentage, sponsorBlockSegments = nowPlaying.sponsorBlockSegments,
                    isFullscreen = false, isPortraitFullscreen = false, onSeek = onSeek, onSeekingChanged = {},
                    labelColor = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onWatchLater, modifier = Modifier.testTag("music-favorite").helpAnchor("music-favorite")) {
                        Icon(watchLaterIcon(video.isWatchLater), stringResource(R.string.music_favorites))
                    }
                    IconButton(onClick = onPrevious, enabled = currentIndex > 0 || player.currentPosition > 5_000) {
                        Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.previous_video), Modifier.size(32.dp))
                    }
                    FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(72.dp).testTag("music-play-pause"), shape = RoundedCornerShape(24.dp)) {
                        if (nowPlaying.isLoadingPlayback || playback.isBuffering) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                        else MorphingPlayPauseIcon(playback.isPlaying, stringResource(if (playback.isPlaying) R.string.pause else R.string.play), Modifier.size(36.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    IconButton(onClick = onNext, enabled = currentIndex in 0 until playback.queueVideoIds.lastIndex) {
                        Icon(Icons.Outlined.SkipNext, stringResource(R.string.next_video), Modifier.size(32.dp))
                    }
                    IconButton(onClick = { onOpenSettings?.invoke() ?: run { showSettings = true } }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.nav_settings)) }
                }
                playback.errorMessage?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { showQueue = true }, modifier = Modifier.testTag("music-open-queue")) { Icon(Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.queue)) }
                    IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, stringResource(R.string.download_all_audio)) }
                    IconButton(onClick = onAddToPlaylist) { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, stringResource(R.string.add_to_playlist)) }
                    if (onFullscreen != null) IconButton(onClick = onFullscreen, modifier = Modifier.testTag("music-fullscreen")) {
                        Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            stringResource(if (fullscreen) R.string.exit_fullscreen else R.string.enter_fullscreen))
                    }
                }
            }
        }
        if (landscape) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { art() }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp)) { item { controls() } }
        } else LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
            item { art() }
            item { controls() }
        }
    }
    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }, contentWindowInsets = { grayjoySheetInsets() }) {
        QueueSectionHeader(Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).navigationBarsPadding()) {
            itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                MusicTrackRow(item, index, item.id == video.id, playback.isPlaying,
                    onClick = { queueActions.onPlay?.invoke(item.id) ?: onPlay(item) }, onLongClick = { onMore(item) }, artwork = true)
            }
        }
    }
    if (showSettings) ModalBottomSheet(onDismissRequest = { showSettings = false }, contentWindowInsets = { grayjoySheetInsets() }) {
        Text(stringResource(R.string.playback_speed), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.navigationBarsPadding()) {
            itemsIndexed(listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { _, speed ->
                ListItem(headlineContent = { Text("${speed}×") }, leadingContent = {
                    RadioButton(selected = playback.playbackSpeed == speed, onClick = { onSpeed(speed); showSettings = false })
                }, modifier = Modifier.combinedClickable(onClick = { onSpeed(speed); showSettings = false }))
            }
        }
    }
}
