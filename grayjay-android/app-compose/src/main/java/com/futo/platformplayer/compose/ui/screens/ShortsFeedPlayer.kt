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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.flow.first

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
    onPrefetchWindowChanged: (List<String>) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val pager = rememberPagerState(initialPage = videos.indexOfFirst { it.id == activeVideoId }.coerceAtLeast(0)) { videos.size }
    val select by rememberUpdatedState(onVideoSelected)
    val loadMore by rememberUpdatedState(onLoadMore)
    val prefetch by rememberUpdatedState(onPrefetchWindowChanged)
    val videoIds = remember(videos) { videos.map { it.id } }
    val currentIds by rememberUpdatedState(videoIds)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var requestedId by remember { mutableStateOf(activeVideoId) }
    val currentRequestedId by rememberUpdatedState(requestedId)
    var anchoredVideoIds by remember { mutableStateOf<List<String>?>(null) }
    DisposableEffect(player) {
        val previous = player.repeatMode
        player.repeatMode = Player.REPEAT_MODE_ONE
        onDispose { player.repeatMode = previous; prefetch(emptyList()) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) prefetch(emptyList())
            if (event == Lifecycle.Event.ON_RESUME) {
                val anchor = currentIds.indexOf(currentRequestedId).takeIf { it >= 0 } ?: pager.settledPage
                prefetch(com.futo.platformplayer.compose.shorts.shortsPrefetchWindow(currentIds, anchor))
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); prefetch(emptyList()) }
    }
    LaunchedEffect(videoIds) {
        // Feed refresh/paging may prepend or reorder cards. The old numerical settledPage can
        // briefly point to another ID before Pager has remeasured its stable keys. Anchor by
        // identity first; dataset changes must never act like an unsolicited user swipe.
        val anchorId = requestedId.takeIf(videoIds::contains)
            ?: activeVideoId.takeIf(videoIds::contains)
            ?: return@LaunchedEffect
        val anchorPage = videoIds.indexOf(anchorId)
        pager.scrollToPage(anchorPage)
        snapshotFlow {
            pager.settledPage == anchorPage && pager.layoutInfo.visiblePagesInfo.any {
                it.index == anchorPage && it.key == anchorId
            }
        }.first { it }
        requestedId = anchorId
        anchoredVideoIds = videoIds
    }
    LaunchedEffect(pager.settledPage, pager.isScrollInProgress, anchoredVideoIds) {
        if (anchoredVideoIds != videoIds || pager.isScrollInProgress) return@LaunchedEffect
        videos.getOrNull(pager.settledPage)?.let { video ->
            if (requestedId != video.id) { requestedId = video.id; select(video) }
        }
    }
    LaunchedEffect(pager.settledPage, pager.isScrollInProgress, anchoredVideoIds, hasMore) {
        if (anchoredVideoIds != videoIds || pager.isScrollInProgress) return@LaunchedEffect
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            prefetch(com.futo.platformplayer.compose.shorts.shortsPrefetchWindow(videoIds, pager.settledPage))
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
