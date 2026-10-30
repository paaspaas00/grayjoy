package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.helpAnchor
import com.futo.platformplayer.compose.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicHomeScreen(home: HomeUiState, recent: List<PlaylistUiModel>, onRefresh: () -> Unit,
    onVideo: (VideoUiModel) -> Unit, onPlaylist: (PlaylistUiModel) -> Unit,
    onChannel: (ChannelUiModel) -> Unit, modifier: Modifier = Modifier,
    onVideoLongClick: (VideoUiModel) -> Unit = {}, favorites: List<VideoUiModel> = emptyList()) {
    PullToRefreshBox(isRefreshing = home.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(state = rememberContentListState(), modifier = Modifier.fillMaxSize().testTag("music-home").helpAnchor("music-home"),
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (recent.isNotEmpty()) item(key = "recent", contentType = "shelf") {
                MusicShelf(stringResource(R.string.recent_playlists)) {
                    items(recent, key = PlaylistUiModel::id) { playlist ->
                        MusicBrowseCard(playlist.title, playlist.thumbnailUrl, false, destination = "playlist:${playlist.id}") { onPlaylist(playlist) }
                    }
                }
            }
            item(key = "favorites", contentType = "shelf") {
                MusicShelf(stringResource(R.string.music_favorites)) {
                    if (favorites.isEmpty()) item { Text(stringResource(R.string.favorites_empty),
                        Modifier.widthIn(max = 320.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(favorites, key = VideoUiModel::id) { video ->
                        MusicBrowseCard(video.title, video.thumbnailUrl, false, onLongClick = { onVideoLongClick(video) }, video = video) { onVideo(video) }
                    }
                }
            }
            if (home.isLoading && home.musicSections.isEmpty()) item(key = "loading") {
                VideoListSkeleton(3, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            home.errorMessage?.let { message -> item(key = "error") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.try_again)) }
                }
            } }
            items(home.musicSections, key = MusicHomeSection::id, contentType = { "shelf" }) { section ->
                MusicShelf(section.title) {
                    items(section.items, key = MusicHomeItem::url) { item ->
                        val track = if (item.type == "track") item.asVideo() else null
                        MusicBrowseCard(item.title, item.thumbnailUrl, item.type == "artist",
                            onLongClick = track?.let { ({ onVideoLongClick(it) }) }, video = track,
                            destination = if (item.type == "artist") "channel:${item.url}" else if (track == null) "playlist:${item.url}" else null) {
                            when (item.type) {
                                "track" -> onVideo(checkNotNull(track))
                                "artist" -> onChannel(item.asChannel())
                                else -> onPlaylist(item.asPlaylist())
                            }
                        }
                    }
                }
            }
            if (!home.isLoading && home.musicSections.isEmpty() && home.errorMessage == null) item {
                Text(stringResource(R.string.home_empty_feed), Modifier.padding(16.dp))
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.try_again)) }
            }
        }
    }
}

@Composable
private fun MusicShelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MusicBrowseCard(title: String, art: String, artist: Boolean, onLongClick: (() -> Unit)? = null,
    video: VideoUiModel? = null, destination: String? = null, onClick: () -> Unit) {
    val performance = com.futo.platformplayer.compose.rememberDevicePerformanceProfile()
    val size = if (performance.isLowEnd && compactUi()) 128.dp else 148.dp
    val previewSize = with(LocalDensity.current) { size.roundToPx().coerceAtMost(if (performance.isLowEnd) 320 else 384) }
    val titleFontSp = MaterialTheme.typography.titleSmall.fontSize.value
    val sourceColor = MaterialTheme.colorScheme.surface
    val source = remember(video, destination, artist, title, art, previewSize, titleFontSp, sourceColor) {
        val model = video ?: destination?.let { VideoUiModel(it.substringAfter(':'), title, "", "", "", sourceId = "spotify", thumbnailUrl = art) }
        model?.let { CardMorphSource(it, true, androidx.compose.ui.unit.IntSize(previewSize, previewSize), titleFontSp, sourceColor,
            destination ?: "video:${it.id}", if (artist) size.value / 2f else 12f) }
    }
    val motion = LocalPlayerCardTransition.current
    Column(Modifier.width(size).then(if (source != null) Modifier.onGloballyPositioned { source.captureCoordinates("surface", it) } else Modifier)
        .combinedClickable(onClick = { source?.let { motion?.arm(it) }; onClick() }, onLongClick = onLongClick)
        .then(if (video != null) Modifier.testTag("music-home-track-${video.id}") else destination?.let { Modifier.testTag("music-home-$it") } ?: Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicArtwork(art, Modifier.size(size).then(if (source != null) Modifier.onGloballyPositioned { source.captureCoordinates("art", it) } else Modifier)
            .then(if (artist) Modifier.clip(CircleShape) else Modifier))
        Text(title, modifier = if (source != null) Modifier.onGloballyPositioned { source.captureCoordinates("title", it) } else Modifier,
            style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
