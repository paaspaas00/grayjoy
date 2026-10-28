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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
                        MusicBrowseCard(playlist.title, playlist.thumbnailUrl, false) { onPlaylist(playlist) }
                    }
                }
            }
            item(key = "favorites", contentType = "shelf") {
                MusicShelf(stringResource(R.string.music_favorites)) {
                    if (favorites.isEmpty()) item { Text(stringResource(R.string.favorites_empty),
                        Modifier.widthIn(max = 320.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(favorites, key = VideoUiModel::id) { video ->
                        MusicBrowseCard(video.title, video.thumbnailUrl, false, onLongClick = { onVideoLongClick(video) }) { onVideo(video) }
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
                        MusicBrowseCard(item.title, item.thumbnailUrl, item.type == "artist",
                            onLongClick = if (item.type == "track") ({ onVideoLongClick(item.asVideo()) }) else null) {
                            when (item.type) {
                                "track" -> onVideo(item.asVideo())
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
private fun MusicBrowseCard(title: String, art: String, artist: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val size = if (com.futo.platformplayer.compose.rememberDevicePerformanceProfile().isLowEnd && compactUi()) 128.dp else 148.dp
    Column(Modifier.width(size).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicArtwork(art, Modifier.size(size).then(if (artist) Modifier.clip(CircleShape) else Modifier))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
