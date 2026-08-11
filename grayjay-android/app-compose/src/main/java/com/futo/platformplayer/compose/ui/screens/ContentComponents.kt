package com.futo.platformplayer.compose.ui.screens

import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.futo.platformplayer.compose.R
import androidx.compose.ui.viewinterop.AndroidView
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.bumptech.glide.Glide
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay

private data class RemoteImageRequestKey(
    val url: String,
    val fallbackUrl: String?,
    val placeholderColor: Int,
    val circleCrop: Boolean,
)

/** Avoid restarting an in-flight Glide request whenever the playback clock recomposes the UI. */
private fun ImageView.loadRemoteImage(
    url: String,
    placeholderColor: Int,
    fallbackUrl: String? = null,
    circleCrop: Boolean = false,
) {
    val key = RemoteImageRequestKey(url, fallbackUrl, placeholderColor, circleCrop)
    if (getTag(R.id.remote_image_request_key) == key) return
    setTag(R.id.remote_image_request_key, key)
    val manager = Glide.with(this)
    val fallback = fallbackUrl?.let { candidate ->
        manager.load(candidate)
            .placeholder(ColorDrawable(placeholderColor))
            .error(ColorDrawable(placeholderColor))
            .let { if (circleCrop) it.circleCrop() else it.centerCrop() }
    }
    manager.load(url)
        .placeholder(ColorDrawable(placeholderColor))
        .let { request ->
            when {
                fallback != null -> request.error(fallback)
                else -> request.error(ColorDrawable(placeholderColor))
            }
        }
        .let { if (circleCrop) it.circleCrop() else it.centerCrop() }
        .into(this)
}

/** Enable AndroidView pooling in lazy lists and detach the previous Glide request on reuse. */
private fun ImageView.resetRemoteImage() {
    Glide.with(this).clear(this)
    setTag(R.id.remote_image_request_key, null)
    setImageDrawable(null)
}

internal fun youtubeThumbnailFallbackUrl(
    sourceId: String,
    videoId: String,
    thumbnailUrl: String,
): String? {
    if (!sourceId.equals("youtube", ignoreCase = true)) return null
    val youtubeId = Regex("/vi(?:_webp)?/([^/?&]+)", RegexOption.IGNORE_CASE)
        .find(thumbnailUrl)?.groupValues?.getOrNull(1)
        ?: Regex("[?&]v=([^&#]+)", RegexOption.IGNORE_CASE)
            .find(videoId)?.groupValues?.getOrNull(1)
        ?: Regex("(?:youtu\\.be/|/shorts/)([^/?&#]+)", RegexOption.IGNORE_CASE)
            .find(videoId)?.groupValues?.getOrNull(1)
        ?: return null
    return "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"
        .takeUnless { it == thumbnailUrl }
}

@Composable
internal fun RequestNextPageEffect(
    listState: LazyListState,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    prefetchItems: Int = 4,
) {
    LaunchedEffect(listState, canLoadMore, prefetchItems) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - prefetchItems
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

@Composable
internal fun SectionHeading(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null) {
            AssistChip(onClick = onAction ?: {}, label = { Text(action) })
        }
    }
}

@Composable
internal fun VideoCard(
    video: VideoUiModel,
    index: Int,
    download: DownloadUiModel? = null,
    metadataText: String = video.metadata,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
    showProgress: Boolean = false,
    animateEntrance: Boolean = true,
) {
    CompactVideoCard(
        video = video,
        index = index,
        download = download,
        metadataText = metadataText,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        showProgress = showProgress,
        animateEntrance = animateEntrance,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactVideoCard(
    video: VideoUiModel,
    index: Int,
    download: DownloadUiModel? = null,
    metadataText: String = video.metadata,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
    showProgress: Boolean = false,
    animateEntrance: Boolean = true,
) {
    val entranceModifier = staggeredVideoEntrance(
        index = index,
        videoId = video.id,
        enabled = animateEntrance,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(entranceModifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("video-card-${video.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            CompactVideoThumbnail(
                video = video,
                index = index,
                showWatchProgress = showProgress,
                modifier = Modifier
                    .width(184.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium),
            )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelAvatarImage(
                        name = video.creator,
                        thumbnailUrl = video.authorThumbnailUrl,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = video.creator,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = metadataText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                }
            }
            download?.takeUnless { it.status == DownloadStatus.Completed }?.let { state ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = downloadStatusText(state),
                        modifier = Modifier.weight(1f),
                        color = if (state.status == DownloadStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.totalParts > 1) {
                        Text(
                            text = "${state.completedParts}/${state.totalParts}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (state.status != DownloadStatus.Failed) {
                    state.progress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download-progress-${video.id}"),
                        )
                    } ?: LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download-progress-${video.id}"),
                    )
                }
            }
        }
    }
}

/**
 * Gives every newly composed page of videos a short, top-to-bottom entrance without
 * changing layout or delaying interaction. Lazy-list item state keeps an item from
 * replaying the animation merely because it scrolled off screen and came back.
 */
@Composable
private fun staggeredVideoEntrance(
    index: Int,
    videoId: String,
    enabled: Boolean,
): Modifier {
    if (!enabled) return Modifier
    var entered by rememberSaveable(videoId) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
        label = "video-card-entrance",
    )
    val entranceDistancePx = with(LocalDensity.current) { 12.dp.toPx() }

    LaunchedEffect(videoId) {
        if (!entered) {
            delay((index % 6) * 24L)
            entered = true
        }
    }
    return Modifier.graphicsLayer {
        alpha = progress
        translationY = entranceDistancePx * (1f - progress)
    }
}

@Composable
private fun CompactVideoThumbnail(
    video: VideoUiModel,
    index: Int,
    showWatchProgress: Boolean,
    modifier: Modifier,
) {
    val gradients = listOf(
        listOf(Color(0xFF152A62), Color(0xFF4F91DC)),
        listOf(Color(0xFF3E1E68), Color(0xFFB05AC4)),
        listOf(Color(0xFF003A42), Color(0xFF1BACC6)),
        listOf(Color(0xFF633014), Color(0xFFE08A45)),
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    Box(
        modifier = modifier.background(Brush.linearGradient(gradients[index % gradients.size])),
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.loadRemoteImage(
                        url = video.thumbnailUrl,
                        placeholderColor = placeholderColor,
                        fallbackUrl = youtubeThumbnailFallbackUrl(
                            sourceId = video.sourceId,
                            videoId = video.id,
                            thumbnailUrl = video.thumbnailUrl,
                        ),
                    )
                },
                onReset = { imageView -> imageView.resetRemoteImage() },
                modifier = Modifier.matchParentSize(),
            )
        }
        if (showWatchProgress && video.watchProgress > 0f) {
            LinearProgressIndicator(
                progress = { video.watchProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("video-progress-${video.id}"),
            )
        }
        SourceIconImage(
            name = video.sourceName.ifBlank { video.sourceId },
            iconUrl = video.sourceIconUrl,
            accentColor = stableSourceColor(video.sourceId),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(22.dp),
        )
        if (video.isDownloaded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                color = Color.Black.copy(alpha = 0.78f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DownloadDone,
                    contentDescription = stringResource(R.string.available_offline),
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
            color = if (video.isLive) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Black.copy(alpha = 0.78f)
            },
            contentColor = Color.White,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = video.duration,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun SourceIconImage(
    name: String,
    iconUrl: String,
    accentColor: Color,
    modifier: Modifier,
) {
    val placeholderColor = accentColor.toArgb()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        if (iconUrl.isNotBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { imageView ->
                    imageView.loadRemoteImage(iconUrl, placeholderColor)
                },
                onReset = { imageView -> imageView.resetRemoteImage() },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun stableSourceColor(sourceId: String): Color = Color(
    0xFF000000L or (sourceId.hashCode().toLong() and 0x00FFFFFFL),
)

@Composable
internal fun ChannelRow(
    channel: ChannelUiModel,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel-${channel.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatarImage(
                name = channel.name,
                thumbnailUrl = channel.thumbnailUrl,
                modifier = Modifier.size(52.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${channel.source} • ${channel.followerCount}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun ChannelAvatarImage(
    name: String,
    thumbnailUrl: String,
    modifier: Modifier,
) {
    val placeholderColor = MaterialTheme.colorScheme.tertiaryContainer.toArgb()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (thumbnailUrl.isNotBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { imageView ->
                    imageView.loadRemoteImage(
                        url = thumbnailUrl,
                        placeholderColor = placeholderColor,
                        circleCrop = true,
                    )
                },
                onReset = { imageView -> imageView.resetRemoteImage() },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistRow(
    playlist: PlaylistUiModel,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onRename: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("playlist-${playlist.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playlist.thumbnailUrl.isNotBlank()) {
                val placeholderColor = MaterialTheme.colorScheme.secondaryContainer.toArgb()
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                    },
                    update = { imageView ->
                        imageView.loadRemoteImage(playlist.thumbnailUrl, placeholderColor)
                    },
                    onReset = { imageView -> imageView.resetRemoteImage() },
                    modifier = Modifier
                        .width(112.dp)
                        .height(64.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(playlist.title, style = MaterialTheme.typography.titleMedium)
                val displayedVideoCount = if (playlist.sourceId.isBlank()) {
                    playlist.videoIds.size
                } else {
                    playlist.videoCount
                }
                Text(
                    pluralStringResource(
                        R.plurals.video_count,
                        displayedVideoCount,
                        displayedVideoCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (playlist.sourceId.isBlank() && onRename != null) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.testTag("rename-playlist-${playlist.id}"),
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.rename_playlist),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoArtwork(
    modifier: Modifier,
    index: Int,
    duration: String,
    isLive: Boolean,
) {
    val gradients = listOf(
        listOf(Color(0xFF152A62), Color(0xFF4F91DC)),
        listOf(Color(0xFF3E1E68), Color(0xFFB05AC4)),
        listOf(Color(0xFF003A42), Color(0xFF1BACC6)),
        listOf(Color(0xFF633014), Color(0xFFE08A45)),
    )
    Box(
        modifier = modifier.background(Brush.linearGradient(gradients[index % gradients.size])),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.38f),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.play_video),
                tint = Color.White,
                modifier = Modifier.padding(16.dp),
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            color = if (isLive) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.78f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                duration,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
