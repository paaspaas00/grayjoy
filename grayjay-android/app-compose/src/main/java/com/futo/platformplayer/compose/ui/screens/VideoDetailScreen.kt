package com.futo.platformplayer.compose.ui.screens

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.NowPlayingUiState
import com.futo.platformplayer.compose.ui.PlaybackUiState
import com.futo.platformplayer.compose.ui.VideoCommentUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.delay

private enum class DetailSection { UpNext, Comments }
private enum class PlayerSettingsPage { Main, Quality, Speed, Subtitles }

@Composable
fun VideoDetailScreen(
    video: VideoUiModel,
    download: DownloadUiModel? = null,
    player: Player,
    playback: PlaybackUiState,
    nowPlaying: NowPlayingUiState,
    queueVideos: List<VideoUiModel>,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleWatchLater: () -> Unit,
    onToggleDownload: () -> Unit = {},
    onDownloadVideo: (Int?) -> Unit = { onToggleDownload() },
    onToggleAudioDownload: () -> Unit = {},
    onDownloadAudio: (Int?) -> Unit = { onToggleAudioDownload() },
    onAddToPlaylist: () -> Unit = {},
    preferredVideoQuality: Int = 0,
    preferredAudioBitrate: Int = Int.MAX_VALUE,
    onToggleFollowing: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (Int?) -> Unit,
    onCaptionsEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String?) -> Unit,
    onRetryPlayback: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    creatorChannel: ChannelUiModel?,
    onCreatorPreview: (ChannelUiModel) -> Unit,
    onCreatorClick: (ChannelUiModel) -> Unit,
    onFullscreen: () -> Unit,
    onLoadMoreRecommendations: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    renderPlayer: Boolean = true,
    onPlayerBoundsChanged: (Rect) -> Unit = {},
) {
    var selectedSectionName by rememberSaveable(video.id) {
        mutableStateOf(DetailSection.UpNext.name)
    }
    var showCreatorSheet by rememberSaveable(video.id) { mutableStateOf(false) }
    val creatorLabel = stringResource(R.string.creator)
    val followerCountLabel = video.authorSubscriberCount?.let { count ->
        pluralStringResource(
            R.plurals.followers_count,
            count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            formatCount(count),
        )
    }
    val fallbackCreatorChannel = remember(
        video.authorUrl,
        video.channelId,
        video.creator,
        video.sourceId,
        video.sourceName,
        video.authorThumbnailUrl,
        followerCountLabel,
        creatorLabel,
    ) {
        ChannelUiModel(
            id = video.authorUrl.ifBlank {
                video.channelId.ifBlank { "${video.sourceId}:${video.creator}" }
            },
            name = video.creator,
            sourceId = video.sourceId,
            source = video.sourceName.ifBlank {
                video.sourceId.replaceFirstChar(Char::uppercase)
            },
            unreadCount = 0,
            followerCount = followerCountLabel ?: creatorLabel,
            description = "",
            thumbnailUrl = video.authorThumbnailUrl,
        )
    }
    val displayedCreatorChannel = creatorChannel ?: fallbackCreatorChannel
    val selectedSection = DetailSection.valueOf(selectedSectionName)
    val detailListState = rememberLazyListState()
    RequestNextPageEffect(
        listState = detailListState,
        canLoadMore = when (selectedSection) {
            DetailSection.UpNext -> nowPlaying.hasMoreRecommendations &&
                !nowPlaying.isLoadingMoreRecommendations
            DetailSection.Comments -> nowPlaying.hasMoreComments && !nowPlaying.isLoadingMoreComments
        },
        onLoadMore = when (selectedSection) {
            DetailSection.UpNext -> onLoadMoreRecommendations
            DetailSection.Comments -> onLoadMoreComments
        },
    )
    val queueIndex = playback.queueVideoIds.indexOf(playback.currentVideoId)
    val canGoPrevious = queueIndex > 0 || playback.positionMs > 5_000L
    val canGoNext = queueIndex >= 0 && queueIndex < playback.queueVideoIds.lastIndex
    val playerSurface: @Composable (Modifier) -> Unit = { modifier ->
        PlayerSurface(
            video = video,
            player = player,
            playback = playback,
            isLoading = nowPlaying.isLoadingPlayback || playback.isBuffering,
            isFullscreen = false,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onTogglePlayback = onTogglePlayback,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onSeekBy = onSeekBy,
            onSeek = onSeek,
            onSpeedChange = onSpeedChange,
            onQualityChange = onQualityChange,
            onCaptionsEnabledChange = onCaptionsEnabledChange,
            onSubtitleLanguageChange = onSubtitleLanguageChange,
            onRetryPlayback = onRetryPlayback,
            onFullscreen = onFullscreen,
            modifier = modifier,
        )
    }
    val playerHost: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                onPlayerBoundsChanged(
                    Rect(
                        left = position.x,
                        top = position.y,
                        right = position.x + coordinates.size.width,
                        bottom = position.y + coordinates.size.height,
                    ),
                )
            },
        ) {
            if (renderPlayer) playerSurface(Modifier.fillMaxSize())
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .widthIn(max = 1_080.dp),
                ) {
                    playerHost(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("video-detail-list"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                ) {
                    videoDetails(
                        video = video,
                        nowPlaying = nowPlaying,
                        queueVideos = queueVideos,
                        selectedSection = selectedSection,
                        onSectionChange = { selectedSectionName = it.name },
                        onToggleWatchLater = onToggleWatchLater,
                        download = download,
                        onToggleDownload = onToggleDownload,
                        onDownloadVideo = onDownloadVideo,
                        onToggleAudioDownload = onToggleAudioDownload,
                        onDownloadAudio = onDownloadAudio,
                        onAddToPlaylist = onAddToPlaylist,
                        preferredVideoQuality = preferredVideoQuality,
                        preferredAudioBitrate = preferredAudioBitrate,
                        availableVideoQualities = playback.availableVideoQualities,
                        onToggleFollowing = onToggleFollowing,
                        onCreatorClick = {
                            onCreatorPreview(displayedCreatorChannel)
                            showCreatorSheet = true
                        },
                        onVideoClick = onVideoClick,
                        onVideoLongClick = onVideoLongClick,
                    )
                    detailPagingIndicator(nowPlaying, selectedSection)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                playerHost(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("video-detail-list"),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    videoDetails(
                        video = video,
                        nowPlaying = nowPlaying,
                        queueVideos = queueVideos,
                        selectedSection = selectedSection,
                        horizontalPadding = 16.dp,
                        onSectionChange = { selectedSectionName = it.name },
                        onToggleWatchLater = onToggleWatchLater,
                        download = download,
                        onToggleDownload = onToggleDownload,
                        onDownloadVideo = onDownloadVideo,
                        onToggleAudioDownload = onToggleAudioDownload,
                        onDownloadAudio = onDownloadAudio,
                        onAddToPlaylist = onAddToPlaylist,
                        preferredVideoQuality = preferredVideoQuality,
                        preferredAudioBitrate = preferredAudioBitrate,
                        availableVideoQualities = playback.availableVideoQualities,
                        onToggleFollowing = onToggleFollowing,
                        onCreatorClick = {
                            onCreatorPreview(displayedCreatorChannel)
                            showCreatorSheet = true
                        },
                        onVideoClick = onVideoClick,
                        onVideoLongClick = onVideoLongClick,
                    )
                    detailPagingIndicator(nowPlaying, selectedSection)
                }
            }
        }
    }

    if (showCreatorSheet) {
        CreatorInfoSheet(
            channel = displayedCreatorChannel,
            isFollowing = nowPlaying.isFollowing,
            onToggleFollowing = onToggleFollowing,
            onDismiss = { showCreatorSheet = false },
            onOpenChannel = {
                showCreatorSheet = false
                onCreatorClick(displayedCreatorChannel)
            },
        )
    }

}

private fun LazyListScope.detailPagingIndicator(
    nowPlaying: NowPlayingUiState,
    section: DetailSection,
) {
    val loading = when (section) {
        DetailSection.UpNext -> nowPlaying.isLoadingMoreRecommendations
        DetailSection.Comments -> nowPlaying.isLoadingMoreComments
    }
    if (loading) item {
        if (section == DetailSection.UpNext) {
            VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth())
        } else {
            SuggestionListSkeleton(count = 2)
        }
    }
}

@Composable
fun FullscreenPlayerScreen(
    video: VideoUiModel,
    player: Player,
    playback: PlaybackUiState,
    isLoadingPlayback: Boolean,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (Int?) -> Unit,
    onCaptionsEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String?) -> Unit,
    onRetryPlayback: () -> Unit,
    onExitFullscreen: () -> Unit,
    portraitFullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val queueIndex = playback.queueVideoIds.indexOf(playback.currentVideoId)
    val canGoPrevious = queueIndex > 0 || playback.positionMs > 5_000L
    val canGoNext = queueIndex >= 0 && queueIndex < playback.queueVideoIds.lastIndex

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val coverOrientationHandoff = shouldCoverFullscreenOrientationHandoff(
                portraitFullscreen = portraitFullscreen,
                viewportWidth = maxWidth.value,
                viewportHeight = maxHeight.value,
            )
            if (coverOrientationHandoff) {
                // Never rotate Compose controls into a portrait window: sheets and system
                // overlays would then use a different coordinate space. Keep this player
                // surface opaque until Android supplies the real landscape viewport.
                return@BoxWithConstraints
            }
            PlayerSurface(
                video = video,
                player = player,
                playback = playback,
                isLoading = isLoadingPlayback || playback.isBuffering,
                isFullscreen = true,
                isPortraitFullscreen = portraitFullscreen,
                canGoPrevious = canGoPrevious,
                canGoNext = canGoNext,
                onTogglePlayback = onTogglePlayback,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSeekBy = onSeekBy,
                onSeek = onSeek,
                onSpeedChange = onSpeedChange,
                onQualityChange = onQualityChange,
                onCaptionsEnabledChange = onCaptionsEnabledChange,
                onSubtitleLanguageChange = onSubtitleLanguageChange,
                onRetryPlayback = onRetryPlayback,
                onFullscreen = onExitFullscreen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun shouldCoverFullscreenOrientationHandoff(
    portraitFullscreen: Boolean,
    viewportWidth: Float,
    viewportHeight: Float,
): Boolean = !portraitFullscreen && viewportHeight > viewportWidth

@Composable
internal fun PlayerSurface(
    video: VideoUiModel,
    player: Player,
    playback: PlaybackUiState,
    isLoading: Boolean,
    isFullscreen: Boolean,
    isPortraitFullscreen: Boolean = false,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (Int?) -> Unit,
    onCaptionsEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String?) -> Unit,
    onRetryPlayback: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier,
    controlsAlpha: Float = 1f,
) {
    var seekProgress by rememberSaveable(video.id) { mutableFloatStateOf(0f) }
    var isSeeking by remember(video.id) { mutableStateOf(false) }
    var controlsVisible by remember(video.id, isFullscreen) { mutableStateOf(true) }
    var isPointerDown by remember(video.id, isFullscreen) { mutableStateOf(false) }
    var interactionSequence by remember(video.id, isFullscreen) { mutableStateOf(0) }
    var seekFeedbackSequence by remember(video.id, isFullscreen) { mutableStateOf(0) }
    var seekFeedbackForward by remember(video.id, isFullscreen) { mutableStateOf(true) }
    var showSettings by rememberSaveable(video.id, isFullscreen) { mutableStateOf(false) }
    var settingsPageName by rememberSaveable(video.id, isFullscreen) {
        mutableStateOf(PlayerSettingsPage.Main.name)
    }
    var controlsLocked by rememberSaveable(video.id, isFullscreen) { mutableStateOf(false) }
    val isPlaylistPlayback = playback.queueVideoIds.size > 1
    val positionProgress = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayedProgress = if (isSeeking) seekProgress else positionProgress
    val displayedPositionMs = if (isSeeking) {
        seekPreviewPositionMs(playback.durationMs, seekProgress)
    } else {
        playback.positionMs
    }
    var seekTooltipWidthPx by remember(video.id, isFullscreen) { mutableStateOf(0) }
    var seekTooltipHeightPx by remember(video.id, isFullscreen) { mutableStateOf(0) }
    var storyboardUnavailable by remember(video.id, video.storyboard) { mutableStateOf(false) }
    val showControls = !controlsLocked &&
        (controlsVisible || playback.errorMessage != null)
    val controlsVisibilityAlpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "player-controls-visibility",
    )

    LaunchedEffect(
        controlsVisible,
        playback.isPlaying,
        playback.errorMessage,
        showSettings,
        isPointerDown,
        isSeeking,
        interactionSequence,
    ) {
        if (
            controlsVisible && playback.errorMessage == null &&
            !showSettings && !isPointerDown && !isSeeking
        ) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsAlpha) {
        if (controlsAlpha < 0.9f) showSettings = false
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            // Seek previews and animated controls must never draw over the Now Playing header.
            .clipToBounds()
            .pointerInput(video.id, isFullscreen) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointerIsDown = event.changes.any { it.pressed }
                        if (pointerIsDown != isPointerDown) {
                            isPointerDown = pointerIsDown
                            if (!pointerIsDown) interactionSequence += 1
                        }
                    }
                }
            }
            .testTag("media-player"),
    ) {
        AndroidView(
            factory = { context ->
                (LayoutInflater.from(context).inflate(
                    R.layout.view_compose_player,
                    null,
                    false,
                ) as PlayerView).apply {
                    useController = false
                    this.player = player
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (video.playbackFromDownload && video.playbackAudioOnly) {
            AudioOnlySpectrogram(
                playback = playback,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (controlsAlpha > 0.01f) Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controlsAlpha.coerceIn(0f, 1f) },
        ) {
        if (isLoading && playback.errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (isFullscreen) 52.dp else 46.dp)
                    .testTag("player-loading"),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
                strokeWidth = 4.dp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(video.id, controlsLocked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { tap ->
                            if (!controlsLocked) {
                                seekFeedbackForward = tap.x >= size.width / 2f
                                seekFeedbackSequence += 1
                                onSeekBy(if (seekFeedbackForward) 10_000L else -10_000L)
                                controlsVisible = true
                            }
                        },
                    )
                },
        )

        if (seekFeedbackSequence > 0) {
            key(seekFeedbackSequence) {
                DoubleTapSeekFeedback(
                    forward = seekFeedbackForward,
                    isFullscreen = isFullscreen,
                    modifier = Modifier
                        .align(if (seekFeedbackForward) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(0.43f),
                )
            }
        }

        if (controlsVisibilityAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controlsVisibilityAlpha },
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.58f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.76f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(
                        if (isFullscreen && isPortraitFullscreen) Modifier.statusBarsPadding()
                        else Modifier,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isFullscreen) {
                    Text(
                        text = video.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(
                    onClick = {
                        settingsPageName = PlayerSettingsPage.Main.name
                        showSettings = true
                        controlsVisible = true
                    },
                    modifier = Modifier
                        .offset(y = if (isFullscreen) 8.dp else 0.dp)
                        .size(if (isFullscreen) 56.dp else 48.dp)
                        .testTag("player-settings"),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.player_settings),
                        tint = Color.White,
                        modifier = Modifier.size(if (isFullscreen) 30.dp else 24.dp),
                    )
                }
                IconButton(
                    onClick = onFullscreen,
                    modifier = Modifier
                        .offset(y = if (isFullscreen) 8.dp else 0.dp)
                        .size(if (isFullscreen) 56.dp else 48.dp)
                        .testTag("player-fullscreen"),
                ) {
                    Icon(
                        if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = stringResource(
                            if (isFullscreen) R.string.exit_fullscreen else R.string.enter_fullscreen,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(if (isFullscreen) 30.dp else 24.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    // The transport row belongs to the area above the seek bar, not to the
                    // geometric centre of the entire surface.
                    .offset(y = if (isFullscreen) (-26).dp else (-24).dp),
                horizontalArrangement = Arrangement.spacedBy(
                    if (isPlaylistPlayback) {
                        if (isFullscreen) 48.dp else 34.dp
                    } else {
                        0.dp
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPlaylistPlayback) {
                    IconButton(
                        onClick = onSkipPrevious,
                        enabled = canGoPrevious,
                        modifier = Modifier.size(if (isFullscreen) 68.dp else 60.dp),
                    ) {
                        Icon(
                            Icons.Outlined.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_video),
                            tint = Color.White.copy(alpha = if (canGoPrevious) 1f else 0.38f),
                            modifier = Modifier.size(if (isFullscreen) 42.dp else 36.dp),
                        )
                    }
                }
                if (isLoading) {
                    Spacer(Modifier.size(if (isFullscreen) 88.dp else 78.dp))
                } else {
                    Surface(
                        modifier = Modifier.size(if (isFullscreen) 88.dp else 78.dp),
                        onClick = onTogglePlayback,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White,
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (playback.isPlaying) R.string.pause else R.string.play,
                            ),
                            modifier = Modifier.padding(if (isFullscreen) 22.dp else 19.dp),
                        )
                    }
                }
                if (isPlaylistPlayback) {
                    IconButton(
                        onClick = onSkipNext,
                        enabled = canGoNext,
                        modifier = Modifier.size(if (isFullscreen) 68.dp else 60.dp),
                    ) {
                        Icon(
                            Icons.Outlined.SkipNext,
                            contentDescription = stringResource(R.string.next_video),
                            tint = Color.White.copy(alpha = if (canGoNext) 1f else 0.38f),
                            modifier = Modifier.size(if (isFullscreen) 42.dp else 36.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(
                        y = when {
                            isFullscreen && isPortraitFullscreen -> (-34).dp
                            isFullscreen -> (-10).dp
                            else -> 0.dp
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (video.isLive) stringResource(R.string.live)
                    else formatPlaybackTime(displayedPositionMs),
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .widthIn(min = if (isFullscreen) 58.dp else 46.dp)
                        .testTag("playback-elapsed"),
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    val density = LocalDensity.current
                    val storyboardWidth = if (isFullscreen) 240.dp else 128.dp
                    val storyboardFrame = remember(
                        video.storyboard,
                        displayedPositionMs,
                        storyboardWidth,
                        density,
                        storyboardUnavailable,
                    ) {
                        if (storyboardUnavailable) null else video.storyboard?.frameAt(
                            positionMs = displayedPositionMs,
                            targetWidthPx = with(density) { storyboardWidth.roundToPx() },
                        )
                    }
                    val showStoryboard = storyboardFrame != null
                    Slider(
                        value = displayedProgress,
                        enabled = !video.isLive && playback.durationMs > 0,
                        onValueChange = {
                            isSeeking = true
                            seekProgress = it
                        },
                        onValueChangeFinished = {
                            onSeek(seekProgress)
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isSeeking) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(2f)
                                .offset {
                                    val horizontalInset = 10.dp.roundToPx()
                                    val trackWidth = (constraints.maxWidth - horizontalInset * 2)
                                        .coerceAtLeast(0)
                                    val thumbCenter = horizontalInset +
                                        (trackWidth * displayedProgress.coerceIn(0f, 1f)).toInt()
                                    val left = (thumbCenter - seekTooltipWidthPx / 2)
                                        .coerceIn(
                                            0,
                                            (constraints.maxWidth - seekTooltipWidthPx)
                                                .coerceAtLeast(0),
                                        )
                                    // Measure the whole preview + timestamp stack so its lower
                                    // edge stays immediately above the seek control. The compact
                                    // portrait card fits in the shorter embedded player.
                                    val top = -(seekTooltipHeightPx + 6.dp.roundToPx())
                                    IntOffset(left, top)
                                }
                                // This popup is visually anchored to the slider but must not
                                // contribute to Box/Row measurement. Otherwise its image height
                                // makes the seek bar and both timestamps jump upward while dragging.
                                .ignoreParentMeasurement()
                                .onGloballyPositioned {
                                    seekTooltipWidthPx = it.size.width
                                    seekTooltipHeightPx = it.size.height
                                }
                                .testTag("player-seek-preview"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (storyboardFrame != null) {
                                Surface(
                                    modifier = Modifier
                                        .size(
                                            width = storyboardWidth,
                                            height = storyboardWidth * (9f / 16f),
                                        )
                                        .testTag("player-seek-storyboard"),
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color.Black,
                                    contentColor = Color.White,
                                    shadowElevation = 7.dp,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color.White.copy(alpha = 0.42f),
                                    ),
                                ) {
                                    AndroidView(
                                        factory = { context -> StoryboardFrameView(context) },
                                        update = { view ->
                                            view.onLoadFailure = {
                                                storyboardUnavailable = true
                                            }
                                            view.showFrame(storyboardFrame)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.size(5.dp))
                            }
                            Surface(
                                modifier = Modifier.testTag("player-seek-time-tooltip"),
                                shape = MaterialTheme.shapes.small,
                                color = Color.Black.copy(alpha = 0.86f),
                                contentColor = Color.White,
                                shadowElevation = 4.dp,
                            ) {
                                Text(
                                    text = formatPlaybackTime(
                                        seekPreviewPositionMs(
                                            playback.durationMs,
                                            seekProgress,
                                        ),
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (video.isLive) "" else formatPlaybackTime(playback.durationMs),
                    color = Color.White,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = if (isFullscreen) 58.dp else 46.dp),
                )
            }
            }
        }

        if (controlsLocked && controlsVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp),
                onClick = {
                    controlsLocked = false
                    controlsVisible = true
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
                contentColor = Color.White,
            ) {
                Icon(
                    Icons.Outlined.LockOpen,
                    contentDescription = stringResource(R.string.unlock_player_controls),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        playback.errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(message, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Button(
                        onClick = onRetryPlayback,
                        modifier = Modifier.testTag("player-retry"),
                    ) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(
            page = PlayerSettingsPage.valueOf(settingsPageName),
            video = video,
            playback = playback,
            onPageChange = { settingsPageName = it.name },
            onDismiss = { showSettings = false },
            onQualityChange = {
                onQualityChange(it)
                showSettings = false
            },
            onSpeedChange = {
                onSpeedChange(it)
                showSettings = false
            },
            onCaptionsEnabledChange = {
                onCaptionsEnabledChange(it)
                showSettings = false
            },
            onSubtitleLanguageChange = {
                onSubtitleLanguageChange(it)
                showSettings = false
            },
            onLockControls = {
                controlsLocked = true
                controlsVisible = true
                showSettings = false
            },
        )
    }
}

private fun Modifier.ignoreParentMeasurement(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, minHeight = 0),
    )
    layout(width = 0, height = 0) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
private fun DoubleTapSeekFeedback(
    forward: Boolean,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 620, easing = LinearOutSlowInEasing),
        )
    }
    val progress = pulse.value
    val contentScale = 0.84f + progress * 0.22f
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = (1f - progress).coerceIn(0f, 1f)
                scaleX = contentScale
                scaleY = contentScale
            }
            .testTag(if (forward) "player-seek-forward-feedback" else "player-seek-back-feedback"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val maximumRadius = size.minDimension * 0.72f
            drawCircle(
                color = Color.White.copy(alpha = 0.20f),
                radius = maximumRadius * (0.36f + progress * 0.64f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.13f),
                radius = maximumRadius * (0.20f + progress * 0.48f),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = if (forward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (isFullscreen) 52.dp else 44.dp),
            )
            Text(
                text = if (forward) "+10" else "−10",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AudioOnlySpectrogram(
    playback: PlaybackUiState,
    modifier: Modifier = Modifier,
) {
    val rowCount = 22
    var spectrumHistory by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
    LaunchedEffect(playback.audioSpectrum) {
        if (playback.audioSpectrum.isNotEmpty()) {
            spectrumHistory = (spectrumHistory + listOf(playback.audioSpectrum)).takeLast(rowCount)
        }
    }
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF090713), Color(0xFF121A25), Color(0xFF06070A)),
                ),
            )
            .testTag("audio-only-visualizer"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spectrumSize = spectrumHistory.maxOfOrNull(List<Float>::size) ?: 0
            if (spectrumSize == 0) return@Canvas
            val leadingEmptyRows = rowCount - spectrumHistory.size
            repeat(rowCount) { row ->
                val depth = row.toFloat() / (rowCount - 1).toFloat()
                val perspective = 0.22f * (1f - depth)
                val left = size.width * perspective
                val right = size.width * (1f - perspective)
                val baseline = size.height * (0.18f + depth * 0.70f)
                val amplitude = size.height * (0.025f + depth * 0.085f)
                val spectrum = if (row < leadingEmptyRows) {
                    emptyList()
                } else {
                    spectrumHistory[row - leadingEmptyRows]
                }
                val path = Path()
                var peakEnergy = 0f
                repeat(spectrumSize) { sample ->
                    val xNorm = sample.toFloat() / (spectrumSize - 1).coerceAtLeast(1).toFloat()
                    val energy = spectrum.getOrElse(sample) { 0f }.coerceIn(0f, 1f)
                    peakEnergy = maxOf(peakEnergy, energy)
                    val x = left + (right - left) * xNorm
                    val y = baseline - energy * amplitude
                    if (sample == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Color.Black.copy(alpha = 0.45f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.5f,
                        cap = StrokeCap.Round,
                    ),
                )
                drawPath(
                    path = path,
                    color = viridis((depth * 0.30f + peakEnergy * 0.70f).coerceIn(0f, 1f)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.2f,
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Black.copy(alpha = 0.54f),
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    stringResource(R.string.audio_only),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private val ViridisColors = listOf(
    Color(0xFF440154),
    Color(0xFF3B528B),
    Color(0xFF21918C),
    Color(0xFF5EC962),
    Color(0xFFFDE725),
)

private fun viridis(value: Float): Color {
    val scaled = value.coerceIn(0f, 1f) * (ViridisColors.size - 1)
    val index = scaled.toInt().coerceAtMost(ViridisColors.lastIndex - 1)
    return lerp(ViridisColors[index], ViridisColors[index + 1], scaled - index)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    page: PlayerSettingsPage,
    video: VideoUiModel,
    playback: PlaybackUiState,
    onPageChange: (PlayerSettingsPage) -> Unit,
    onDismiss: () -> Unit,
    onQualityChange: (Int?) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onCaptionsEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String?) -> Unit,
    onLockControls: () -> Unit,
) {
    val qualityDescription = playback.selectedVideoQuality?.let { "${it}p" }
        ?: playback.currentVideoHeight?.let { stringResource(R.string.auto_quality, it) }
        ?: stringResource(R.string.auto)
    val subtitleDescription = when {
        video.subtitleTracks.isEmpty() -> stringResource(R.string.not_available)
        !playback.captionsEnabled -> stringResource(R.string.off)
        playback.selectedSubtitleTrackIndex != null -> video.subtitleTracks
            .getOrNull(playback.selectedSubtitleTrackIndex)
            ?.name
            ?.ifBlank {
                video.subtitleTracks.getOrNull(playback.selectedSubtitleTrackIndex)
                    ?.language.orEmpty()
            }
            ?: stringResource(R.string.on)
        playback.selectedSubtitleLanguage != null -> video.subtitleTracks
            .firstOrNull { it.language == playback.selectedSubtitleLanguage }
            ?.name
            ?.ifBlank { playback.selectedSubtitleLanguage }
            ?: playback.selectedSubtitleLanguage
        else -> video.subtitleTracks.firstOrNull()?.name
            ?.ifBlank { stringResource(R.string.on) }
            ?: stringResource(R.string.on)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (fadeIn(tween(180)) + slideInHorizontally { width ->
                    if (forward) width / 7 else -width / 7
                }).togetherWith(
                    fadeOut(tween(120)) + slideOutHorizontally { width ->
                        if (forward) -width / 9 else width / 9
                    },
                )
            },
            label = "player-settings-page",
        ) { animatedPage ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (animatedPage != PlayerSettingsPage.Main) {
                    IconButton(onClick = { onPageChange(PlayerSettingsPage.Main) }) {
                        Icon(
                            Icons.Outlined.ChevronLeft,
                            contentDescription = stringResource(R.string.back_to_player_settings),
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Text(
                    text = when (animatedPage) {
                        PlayerSettingsPage.Main -> stringResource(R.string.player_settings)
                        PlayerSettingsPage.Quality -> stringResource(R.string.quality)
                        PlayerSettingsPage.Speed -> stringResource(R.string.playback_speed)
                        PlayerSettingsPage.Subtitles -> stringResource(R.string.subtitles)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            when (animatedPage) {
                PlayerSettingsPage.Main -> {
                    PlayerSettingRow(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.quality),
                        value = qualityDescription,
                        testTag = "player-settings-quality",
                        onClick = { onPageChange(PlayerSettingsPage.Quality) },
                    )
                    PlayerSettingRow(
                        icon = Icons.Outlined.Speed,
                        title = stringResource(R.string.playback_speed),
                        value = formatSpeed(playback.playbackSpeed),
                        testTag = "player-settings-speed",
                        onClick = { onPageChange(PlayerSettingsPage.Speed) },
                    )
                    PlayerSettingRow(
                        icon = Icons.Outlined.ClosedCaption,
                        title = stringResource(R.string.subtitles),
                        value = subtitleDescription,
                        enabled = video.subtitleTracks.isNotEmpty(),
                        testTag = "player-settings-subtitles",
                        onClick = { onPageChange(PlayerSettingsPage.Subtitles) },
                    )
                    PlayerSettingRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.lock_controls),
                        testTag = "player-settings-lock",
                        showChevron = false,
                        onClick = onLockControls,
                    )
                }

                PlayerSettingsPage.Quality -> {
                    PlayerSelectionRow(
                        title = stringResource(R.string.auto),
                        detail = playback.currentVideoHeight?.let {
                            stringResource(R.string.currently_quality, it)
                        },
                        selected = playback.selectedVideoQuality == null,
                        onClick = { onQualityChange(null) },
                    )
                    playback.availableVideoQualities.forEach { height ->
                        PlayerSelectionRow(
                            title = "${height}p",
                            selected = playback.selectedVideoQuality == height,
                            onClick = { onQualityChange(height) },
                        )
                    }
                }

                PlayerSettingsPage.Speed -> {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        PlayerSelectionRow(
                            title = if (speed == 1f) stringResource(R.string.normal) else formatSpeed(speed),
                            detail = if (speed == 1f) formatSpeed(speed) else null,
                            selected = playback.playbackSpeed == speed,
                            onClick = { onSpeedChange(speed) },
                        )
                    }
                }

                PlayerSettingsPage.Subtitles -> {
                    PlayerSelectionRow(
                        title = stringResource(R.string.off),
                        selected = !playback.captionsEnabled,
                        onClick = { onSubtitleLanguageChange(null) },
                    )
                    video.subtitleTracks.forEachIndexed { index, subtitle ->
                        val selected = playback.captionsEnabled && when {
                            playback.selectedSubtitleTrackIndex != null ->
                                playback.selectedSubtitleTrackIndex == index
                            playback.selectedSubtitleLanguage != null ->
                                playback.selectedSubtitleLanguage == subtitle.language
                            else -> index == 0
                        }
                        PlayerSelectionRow(
                            title = subtitle.name.ifBlank {
                                subtitle.language?.uppercase()
                                    ?: stringResource(R.string.captions_number, index + 1)
                            },
                            detail = subtitle.language?.uppercase(),
                            selected = selected,
                            onClick = {
                                onSubtitleLanguageChange(subtitle.uri)
                            },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PlayerSettingRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    enabled: Boolean = true,
    testTag: String,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(30.dp))
        Text(title, color = contentColor, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        value?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (showChevron) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = contentColor)
        }
    }
}

@Composable
private fun PlayerSelectionRow(
    title: String,
    detail: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.selected))
        }
    }
}

private fun LazyListScope.videoDetails(
    video: VideoUiModel,
    download: DownloadUiModel?,
    nowPlaying: NowPlayingUiState,
    queueVideos: List<VideoUiModel>,
    selectedSection: DetailSection,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onSectionChange: (DetailSection) -> Unit,
    onToggleWatchLater: () -> Unit,
    onToggleDownload: () -> Unit,
    onDownloadVideo: (Int?) -> Unit,
    onToggleAudioDownload: () -> Unit,
    onDownloadAudio: (Int?) -> Unit,
    onAddToPlaylist: () -> Unit,
    preferredVideoQuality: Int,
    preferredAudioBitrate: Int,
    availableVideoQualities: List<Int>,
    onToggleFollowing: () -> Unit,
    onCreatorClick: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
) {
    item {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(video.title, style = MaterialTheme.typography.titleLarge)
            if (video.metadata.isNotBlank()) {
                Text(
                    video.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    item {
        VideoActions(
            video = video,
            download = download,
            horizontalPadding = horizontalPadding,
            onToggleWatchLater = onToggleWatchLater,
            onToggleDownload = onToggleDownload,
            onDownloadVideo = onDownloadVideo,
            onToggleAudioDownload = onToggleAudioDownload,
            onDownloadAudio = onDownloadAudio,
            onAddToPlaylist = onAddToPlaylist,
            preferredVideoQuality = preferredVideoQuality,
            preferredAudioBitrate = preferredAudioBitrate,
            availableVideoQualities = availableVideoQualities,
        )
    }
    item {
        CreatorCard(
            video = video,
            isFollowing = nowPlaying.isFollowing,
            onToggleFollowing = onToggleFollowing,
            onClick = onCreatorClick,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
    }
    if (video.description.isNotBlank()) {
        item {
            VideoDescriptionCard(
                description = video.description,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )
        }
    }
    item {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedSection == DetailSection.UpNext,
                onClick = { onSectionChange(DetailSection.UpNext) },
                modifier = Modifier.testTag("now-playing-section-up-next"),
                label = { Text(stringResource(R.string.up_next)) },
            )
            FilterChip(
                selected = selectedSection == DetailSection.Comments,
                onClick = { onSectionChange(DetailSection.Comments) },
                modifier = Modifier.testTag("now-playing-section-comments"),
                label = {
                    val exactCount = exactCommentCount(
                        loadedCount = nowPlaying.comments.size,
                        hasMore = nowPlaying.hasMoreComments,
                    )
                    Text(
                        if (exactCount == null) stringResource(R.string.comments)
                        else stringResource(R.string.comments_with_count, exactCount),
                    )
                },
            )
        }
    }
    nowPlaying.errorMessage?.let { message ->
        item {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = horizontalPadding),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    when (selectedSection) {
        DetailSection.UpNext -> {
            val currentId = video.id
            val queue = queueVideos.filter { it.id != currentId }
            val recommendations = nowPlaying.recommendations.filter { recommendation ->
                recommendation.id != currentId && queue.none { it.id == recommendation.id }
            }
            if (queue.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.queue),
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                queue.forEachIndexed { index, queuedVideo ->
                    item(key = "queue-${queuedVideo.id}") {
                        Box(Modifier.padding(horizontal = horizontalPadding)) {
                            CompactVideoCard(
                                video = queuedVideo,
                                index = index,
                                onClick = { onVideoClick(queuedVideo) },
                                onLongClick = { onVideoLongClick(queuedVideo) },
                            )
                        }
                    }
                }
            }
            if (nowPlaying.isLoadingExtras) {
                item {
                    Box(
                        Modifier
                            .padding(horizontal = horizontalPadding)
                            .testTag("now-playing-extras-loading"),
                    ) {
                        VideoListSkeleton(count = 3, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (recommendations.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recommended),
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                recommendations.forEachIndexed { index, recommendation ->
                    item(key = "recommended-${recommendation.id}") {
                        Box(Modifier.padding(horizontal = horizontalPadding)) {
                            CompactVideoCard(
                                video = recommendation,
                                index = index,
                                onClick = { onVideoClick(recommendation) },
                                onLongClick = { onVideoLongClick(recommendation) },
                            )
                        }
                    }
                }
            } else if (!nowPlaying.isLoadingExtras && queue.isEmpty()) {
                item {
                    SectionMessage(
                        title = stringResource(R.string.nothing_queued),
                        body = stringResource(R.string.nothing_queued_body),
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }
        }
        DetailSection.Comments -> {
            if (nowPlaying.isLoadingExtras) {
                item {
                    CommentListSkeleton(
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            } else if (nowPlaying.comments.isNotEmpty()) {
                nowPlaying.comments.forEachIndexed { index, comment ->
                    item(key = "comment-$index-${comment.author}") {
                        CommentCard(comment, Modifier.padding(horizontal = horizontalPadding))
                    }
                }
            } else if (!nowPlaying.isLoadingExtras) {
                item {
                    SectionMessage(
                        title = stringResource(
                            if (nowPlaying.commentsAvailable) R.string.no_comments_yet
                            else R.string.comments_unavailable,
                        ),
                        body = if (nowPlaying.commentsAvailable) {
                            stringResource(R.string.no_comments_body)
                        } else {
                            stringResource(R.string.comments_unsupported_body)
                        },
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }
        }
    }
}

internal fun exactCommentCount(loadedCount: Int, hasMore: Boolean): Int? =
    loadedCount.takeIf { it > 0 && !hasMore }

@Composable
private fun VideoActions(
    video: VideoUiModel,
    download: DownloadUiModel?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onToggleWatchLater: () -> Unit,
    onToggleDownload: () -> Unit,
    onDownloadVideo: (Int?) -> Unit,
    onToggleAudioDownload: () -> Unit,
    onDownloadAudio: (Int?) -> Unit,
    onAddToPlaylist: () -> Unit,
    preferredVideoQuality: Int,
    preferredAudioBitrate: Int,
    availableVideoQualities: List<Int>,
) {
    val context = LocalContext.current
    var showDownloadOptions by rememberSaveable(video.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        video.likeCount?.let { likes ->
            NowPlayingActionButton(
                icon = Icons.Outlined.ThumbUp,
                label = formatCount(likes),
            )
        }
        NowPlayingActionButton(
            icon = Icons.Outlined.BookmarkBorder,
            label = stringResource(if (video.isWatchLater) R.string.saved else R.string.watch_later),
            onClick = onToggleWatchLater,
            modifier = Modifier.testTag("toggle-watch-later"),
            iconContentDescription = if (video.isWatchLater) {
                stringResource(R.string.remove_watch_later)
            } else {
                stringResource(R.string.add_watch_later)
            },
            progress = if (video.isWatchLater) 1f else 0f,
        )
        if (!video.isLive) {
            DownloadProgressChip(
                download = download,
                onClick = { showDownloadOptions = true },
            )
        }
        NowPlayingActionButton(
            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
            label = stringResource(R.string.add_to_playlist),
            onClick = onAddToPlaylist,
            modifier = Modifier.testTag("now-playing-add-to-playlist"),
            iconContentDescription = stringResource(R.string.add_to_playlist),
        )
        NowPlayingActionButton(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.share),
            onClick = { shareVideo(context, video) },
            modifier = Modifier.testTag("share-video"),
            iconContentDescription = stringResource(R.string.share_video),
        )
    }
    if (showDownloadOptions && !video.isLive) {
        DownloadOptionsSheet(
            download = download,
            preferredVideoQuality = preferredVideoQuality,
            preferredAudioBitrate = preferredAudioBitrate,
            availableVideoQualities = (
                availableVideoQualities + video.qualityVariants.map { it.height }
                ).filter { it > 0 }.distinct().sortedDescending(),
            availableAudioBitrates = video.audioQualityVariants
                .map { it.bitrate }
                .filter { it > 0 }
                .distinct()
                .sortedDescending()
                .ifEmpty { DefaultAudioBitrates },
            onDismiss = { showDownloadOptions = false },
            onToggleVideoDownload = onToggleDownload,
            onDownloadVideo = onDownloadVideo,
            onToggleAudioDownload = onToggleAudioDownload,
            onDownloadAudio = onDownloadAudio,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    download: DownloadUiModel?,
    preferredVideoQuality: Int,
    preferredAudioBitrate: Int,
    availableVideoQualities: List<Int>,
    availableAudioBitrates: List<Int>,
    onDismiss: () -> Unit,
    onToggleVideoDownload: () -> Unit,
    onDownloadVideo: (Int?) -> Unit,
    onToggleAudioDownload: () -> Unit,
    onDownloadAudio: (Int?) -> Unit,
) {
    var mediaTypeName by rememberSaveable { mutableStateOf(DownloadMediaType.Video.name) }
    var useAdvancedQuality by rememberSaveable { mutableStateOf(false) }
    var selectedHeight by rememberSaveable(availableVideoQualities) {
        mutableStateOf(
            preferredVideoQuality.takeIf { it in availableVideoQualities }
                ?: availableVideoQualities.firstOrNull(),
        )
    }
    var selectedAudioBitrate by rememberSaveable(availableAudioBitrates) {
        mutableStateOf(
            preferredAudioBitrate.takeIf { it in availableAudioBitrates }
                ?: if (preferredAudioBitrate == 1) availableAudioBitrates.lastOrNull()
                else availableAudioBitrates.firstOrNull(),
        )
    }
    val mediaType = DownloadMediaType.valueOf(mediaTypeName)
    val isComplete = download?.isComplete(mediaType) == true
    val isActive = download?.isActive(mediaType) == true
    val isFailed = mediaType in download?.failedMediaTypes.orEmpty()
    val actionLabel = stringResource(
        when {
            isComplete -> R.string.remove_download
            isActive -> R.string.cancel_download
            isFailed -> R.string.retry_download
            mediaType == DownloadMediaType.Audio -> R.string.download_all_audio
            else -> R.string.download_all_video
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.download_options), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.choose_download_format),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mediaType == DownloadMediaType.Video,
                    onClick = { mediaTypeName = DownloadMediaType.Video.name },
                    label = { Text(stringResource(R.string.video)) },
                    leadingIcon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) },
                    modifier = Modifier.testTag("download-format-video"),
                )
                FilterChip(
                    selected = mediaType == DownloadMediaType.Audio,
                    onClick = { mediaTypeName = DownloadMediaType.Audio.name },
                    label = { Text(stringResource(R.string.audio_only)) },
                    leadingIcon = { Icon(Icons.Outlined.MusicNote, contentDescription = null) },
                    modifier = Modifier.testTag("download-format-audio"),
                )
            }

            if (mediaType == DownloadMediaType.Video) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DownloadQualityModeRow(
                        title = stringResource(R.string.app_quality_setting),
                        detail = stringResource(
                            R.string.app_quality_setting_value,
                            if (preferredVideoQuality > 0) "${preferredVideoQuality}p"
                            else stringResource(R.string.automatic),
                        ),
                        selected = !useAdvancedQuality,
                        onClick = { useAdvancedQuality = false },
                    )
                    DownloadQualityModeRow(
                        title = stringResource(R.string.advanced_quality),
                        detail = stringResource(R.string.choose_video_quality),
                        selected = useAdvancedQuality,
                        enabled = availableVideoQualities.isNotEmpty(),
                        onClick = { useAdvancedQuality = true },
                    )
                }
                if (useAdvancedQuality) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableVideoQualities.forEach { height ->
                            FilterChip(
                                selected = selectedHeight == height,
                                onClick = { selectedHeight = height },
                                label = { Text("${height}p") },
                                modifier = Modifier.testTag("download-quality-$height"),
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DownloadQualityModeRow(
                        title = stringResource(R.string.app_quality_setting),
                        detail = stringResource(
                            R.string.app_audio_quality_setting_value,
                            stringResource(
                                if (preferredAudioBitrate == 1) R.string.low_data
                                else R.string.high_quality,
                            ),
                        ),
                        selected = !useAdvancedQuality,
                        onClick = { useAdvancedQuality = false },
                    )
                    DownloadQualityModeRow(
                        title = stringResource(R.string.advanced_quality),
                        detail = stringResource(R.string.choose_audio_quality),
                        selected = useAdvancedQuality,
                        enabled = availableAudioBitrates.isNotEmpty(),
                        onClick = { useAdvancedQuality = true },
                    )
                }
                if (useAdvancedQuality) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableAudioBitrates.forEach { bitrate ->
                            FilterChip(
                                selected = selectedAudioBitrate == bitrate,
                                onClick = { selectedAudioBitrate = bitrate },
                                label = { Text(formatAudioBitrate(bitrate)) },
                                modifier = Modifier.testTag("download-audio-quality-$bitrate"),
                            )
                        }
                    }
                }
            }

            download?.takeIf { it.hasAttempt(mediaType) }?.let {
                Text(
                    downloadStatusText(it, mediaType),
                    color = if (isFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    when {
                        mediaType == DownloadMediaType.Audio && (isComplete || isActive) ->
                            onToggleAudioDownload()
                        mediaType == DownloadMediaType.Audio ->
                            onDownloadAudio(selectedAudioBitrate.takeIf { useAdvancedQuality })
                        isComplete || isActive -> onToggleVideoDownload()
                        else -> onDownloadVideo(selectedHeight.takeIf { useAdvancedQuality })
                    }
                    onDismiss()
                },
                enabled = !useAdvancedQuality || when (mediaType) {
                    DownloadMediaType.Video -> selectedHeight != null
                    DownloadMediaType.Audio -> selectedAudioBitrate != null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm-download-option"),
            ) {
                Icon(
                    if (isComplete || isActive) Icons.Outlined.Close else Icons.Outlined.Download,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(actionLabel)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

private val DefaultAudioBitrates = listOf(320_000, 256_000, 192_000, 160_000, 128_000, 96_000, 64_000)

private fun formatAudioBitrate(bitrate: Int): String = when {
    bitrate >= 1_000_000 -> "${bitrate / 1_000_000f} Mbps"
    bitrate >= 1_000 -> "${bitrate / 1_000} kbps"
    else -> "$bitrate bps"
}

@Composable
private fun DownloadQualityModeRow(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.38f,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DownloadProgressChip(
    download: DownloadUiModel?,
    onClick: () -> Unit,
) {
    val state = nowPlayingDownloadChipState(download)
    val targetProgress = state.progress
    val progress by animateFloatAsState(targetValue = targetProgress, label = "download-progress")
    NowPlayingActionButton(
        icon = when {
            state.isActive -> Icons.Outlined.Close
            state.isComplete -> Icons.Outlined.DownloadDone
            else -> Icons.Outlined.Download
        },
        label = stringResource(
            when {
                state.isActive -> R.string.cancel_download
                state.videoComplete -> R.string.remove_download
                state.audioComplete -> R.string.available_offline
                state.isFailed -> R.string.retry_download
                else -> R.string.download
            },
        ),
        onClick = onClick,
        progress = progress,
        modifier = Modifier.testTag("toggle-download"),
    )
}

@Composable
private fun NowPlayingActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    iconContentDescription: String? = null,
    progress: Float = 0f,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (progress > 0f) {
            val progressColor = MaterialTheme.colorScheme.primaryContainer
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    color = progressColor,
                    size = size.copy(width = size.width * progress.coerceIn(0f, 1f)),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal data class NowPlayingDownloadChipState(
    val progress: Float,
    val isActive: Boolean,
    val isComplete: Boolean,
    val isFailed: Boolean,
    val videoComplete: Boolean,
    val audioComplete: Boolean,
)

internal fun nowPlayingDownloadChipState(
    download: DownloadUiModel?,
): NowPlayingDownloadChipState {
    val videoComplete = download?.isComplete(DownloadMediaType.Video) == true
    val audioComplete = download?.isComplete(DownloadMediaType.Audio) == true
    val isActive = download?.let {
        it.isActive(DownloadMediaType.Video) || it.isActive(DownloadMediaType.Audio)
    } == true
    val isFailed = download?.failedMediaTypes?.isNotEmpty() == true
    val progress = when {
        // An audio transfer started after a completed video (or vice versa) must show the active
        // transfer's progress instead of remaining fully filled by the older completed media.
        isActive -> download?.progress ?: 0f
        videoComplete || audioComplete -> 1f
        else -> 0f
    }.coerceIn(0f, 1f)
    return NowPlayingDownloadChipState(
        progress = progress,
        isActive = isActive,
        isComplete = videoComplete || audioComplete,
        isFailed = isFailed,
        videoComplete = videoComplete,
        audioComplete = audioComplete,
    )
}

@Composable
private fun CreatorCard(
    video: VideoUiModel,
    isFollowing: Boolean,
    onToggleFollowing: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("now-playing-creator"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelAvatarImage(
                name = video.creator,
                thumbnailUrl = video.authorThumbnailUrl,
                modifier = Modifier.size(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(video.creator, style = MaterialTheme.typography.titleMedium)
                video.authorSubscriberCount?.let { subscribers ->
                    Text(
                        pluralStringResource(
                            R.plurals.followers_count,
                            subscribers.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            formatCount(subscribers),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = onToggleFollowing,
                modifier = Modifier.testTag("creator-follow"),
            ) {
                Text(stringResource(if (isFollowing) R.string.following else R.string.follow))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorInfoSheet(
    channel: ChannelUiModel,
    isFollowing: Boolean,
    onToggleFollowing: () -> Unit,
    onDismiss: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelAvatarImage(
                name = channel.name,
                thumbnailUrl = channel.thumbnailUrl,
                modifier = Modifier.size(88.dp),
            )
            Text(
                channel.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "${channel.source} • ${channel.followerCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (channel.description.isNotBlank()) {
                Text(
                    channel.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onToggleFollowing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (isFollowing) R.string.following else R.string.follow))
                }
                Button(
                    onClick = onOpenChannel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open-creator-channel"),
                ) {
                    Text(stringResource(R.string.view_channel))
                }
            }
        }
    }
}

@Composable
private fun VideoDescriptionCard(description: String, modifier: Modifier) {
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.about_video), style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
            }
        }
    }
}

@Composable
private fun CommentCard(comment: VideoCommentUiModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(comment.author.take(1), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                listOf(comment.author, comment.age).filter(String::isNotBlank).joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(comment.message, style = MaterialTheme.typography.bodyMedium)
            val responseText = buildList {
                comment.likeCount?.let {
                    add(
                        pluralStringResource(
                            R.plurals.likes_count,
                            it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            formatCount(it),
                        ),
                    )
                }
                comment.replyCount?.takeIf { it > 0 }?.let {
                    add(pluralStringResource(R.plurals.replies_count, it, it))
                }
            }.joinToString(" • ")
            if (responseText.isNotBlank()) {
                Text(
                    responseText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


private fun shareVideo(context: Context, video: VideoUiModel) {
    val url = video.shareUrl.ifBlank { video.contentUrl.ifBlank { video.id } }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, video.title)
        putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_video)))
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

internal fun seekPreviewPositionMs(durationMs: Long, progress: Float): Long =
    if (durationMs <= 0L) 0L
    else (durationMs * progress.coerceIn(0f, 1f).toDouble()).toLong()

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"

private fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).trimEnd('0').trimEnd('.')
    else -> value.toString()
}
