package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.futo.platformplayer.compose.ui.VideoUiModel

internal fun supportsShortsFeedPlayer(widthDp: Int, heightDp: Int, smallestWidthDp: Int = minOf(widthDp, heightDp)) =
    smallestWidthDp in 1..599 && minOf(widthDp, heightDp) > 0

@Composable
internal fun ShortsFeedPlayer(
    videos: List<VideoUiModel>,
    activeVideoId: String,
    player: Player,
    onVideoSelected: (VideoUiModel) -> Unit,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pager = rememberPagerState(initialPage = videos.indexOfFirst { it.id == activeVideoId }.coerceAtLeast(0)) { videos.size }
    val select by rememberUpdatedState(onVideoSelected)
    val loadMore by rememberUpdatedState(onLoadMore)
    var requestedId by remember { mutableStateOf(activeVideoId) }
    DisposableEffect(player) {
        val previous = player.repeatMode
        player.repeatMode = Player.REPEAT_MODE_ONE
        onDispose { player.repeatMode = previous }
    }
    LaunchedEffect(pager.settledPage, videos.size, hasMore) {
        videos.getOrNull(pager.settledPage)?.let { video ->
            if (requestedId != video.id) { requestedId = video.id; select(video) }
        }
        if (hasMore && pager.settledPage >= videos.size - 3) loadMore()
    }
    VerticalPager(state = pager, key = { videos[it].id }, beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize().background(Color.Black).testTag("shorts-fullscreen-pager")) { page ->
        val video = videos[page]
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (page == pager.settledPage && video.id == activeVideoId) content()
            else {
                if (video.thumbnailUrl.isNotBlank()) RemoteBitmapImage(video.thumbnailUrl, Color.Black,
                    Modifier.fillMaxSize(), requestHeaders = video.thumbnailRequestHeaders)
                Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp))
            }
        }
    }
}
