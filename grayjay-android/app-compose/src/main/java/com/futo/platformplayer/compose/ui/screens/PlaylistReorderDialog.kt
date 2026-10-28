package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.PlayArrow
import com.futo.platformplayer.compose.onboarding.helpAnchor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DROP_SLOT_KEY = "drop-slot"

@Composable
internal fun ReorderPlaylistDialog(
    videos: List<VideoUiModel>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    currentVideoId: String? = null,
    isPlaying: Boolean = false,
    shuffled: Boolean = false,
    onShuffleChange: (Boolean) -> Unit = {},
    onOrderChange: (List<String>) -> Unit = {},
    onPlaybackOrderChange: (List<String>) -> Unit = {},
    onPlayOrder: (List<String>) -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val currentIds = remember(videos) { videos.map(VideoUiModel::id).distinct() }
    val totalDuration = remember(videos) { musicDuration(videos) }
    var normalOrder by rememberSaveable { mutableStateOf(videos.map(VideoUiModel::id).distinct()) }
    var orderedIds by rememberSaveable { mutableStateOf(if (shuffled) shuffledReorderIds(normalOrder, currentVideoId) else normalOrder) }
    var shuffleEnabled by rememberSaveable { mutableStateOf(shuffled) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var shuffleScrollJob by remember { mutableStateOf<Job?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    val videosById = remember(videos) { videos.associateBy(VideoUiModel::id) }
    val listState = rememberLazyListState()
    RequestNextPageEffect(listState, canLoadMore = hasMore && !isLoadingMore, onLoadMore = onLoadMore)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgePx = with(density) { 48.dp.toPx() }
    val hysteresisPx = with(density) { 5.dp.toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var slotIndex by remember { mutableIntStateOf(0) }
    var dropping by remember { mutableStateOf(false) }
    var dropJob by remember { mutableStateOf<Job?>(null) }
    val entries = remember(orderedIds, draggedId, slotIndex) {
        if (draggedId == null) orderedIds.map { it as String? }
        else orderedIds.filterNot { it == draggedId }.map { it as String? }.toMutableList()
            .apply { add(slotIndex.coerceIn(0, size), null) }
    }

    fun finish() {
        if (draggedId != null || dropping) return
        onConfirm(reconcileReorderDraft(orderedIds, currentIds))
    }

    fun dismiss() {
        if (orderedIds != currentIds) confirmDiscard = true else onDismiss()
    }

    fun cancelDrag() {
        dropJob?.cancel()
        draggedId = null
        dropping = false
    }
    LaunchedEffect(currentIds) {
        orderedIds = reconcileReorderDraft(orderedIds, videos.map(VideoUiModel::id))
        normalOrder = reconcileReorderDraft(normalOrder, videos.map(VideoUiModel::id))
        if (draggedId != null && draggedId !in orderedIds) cancelDrag()
        slotIndex = slotIndex.coerceIn(0, (orderedIds.size - 1).coerceAtLeast(0))
    }
    fun updateSlot() {
        if (draggedId == null || dropping) return
        val visible = listState.layoutInfo.visibleItemsInfo
        val slot = visible.firstOrNull { it.key == DROP_SLOT_KEY }
        if (slot == null) {
            visible.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragTop - dragHeight / 2f) }
                ?.let { slotIndex = it.index.coerceIn(0, (orderedIds.size - 1).coerceAtLeast(0)) }
            return
        }
        // Wait for the last slot move to be measured. Stale geometry must not undo a move.
        if (slot.index != slotIndex) return
        val previous = visible.firstOrNull { it.index == slot.index - 1 }
        val next = visible.firstOrNull { it.index == slot.index + 1 }
        val delta = reorderSlotDelta(
            dragTop + dragHeight / 2f,
            previous?.let { it.offset + it.size / 2f },
            next?.let { it.offset + it.size / 2f },
            hysteresisPx,
        )
        slotIndex = (slotIndex + delta).coerceIn(0, (orderedIds.size - 1).coerceAtLeast(0))
    }
    fun finishDrag() {
        val id = draggedId ?: return
        dropping = true
        dropJob = scope.launch {
            withFrameNanos { }
            val destination = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == DROP_SLOT_KEY }?.offset?.toFloat() ?: dragTop
            animate(dragTop, destination, animationSpec = tween(150, easing = FastOutSlowInEasing)) { value, _ ->
                dragTop = value
            }
            if (draggedId == id) {
                orderedIds = commitReorderSlot(orderedIds, id, slotIndex)
                normalOrder = orderedIds
                shuffleEnabled = false
                draggedId = null
                dropping = false
            }
        }
    }
    LaunchedEffect(draggedId, dropping) {
        if (draggedId == null || dropping) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (draggedId != null && !dropping) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrame = frame
            val layout = listState.layoutInfo
            val overshoot = when {
                dragTop < layout.viewportStartOffset + edgePx -> dragTop - layout.viewportStartOffset - edgePx
                dragTop + dragHeight > layout.viewportEndOffset - edgePx -> dragTop + dragHeight - layout.viewportEndOffset + edgePx
                else -> 0f
            }
            if (overshoot != 0f) listState.scrollBy(overshoot.coerceIn(-edgePx, edgePx) * 12f * seconds)
            updateSlot()
        }
    }

    Dialog(
        onDismissRequest = { cancelDrag(); dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      val view = LocalView.current
      val lightBars = MaterialTheme.colorScheme.surface.luminance() > .5f
      DisposableEffect(view, lightBars) {
          val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
          val applyBars = Runnable {
              window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view).apply {
                  isAppearanceLightStatusBars = lightBars
                  isAppearanceLightNavigationBars = lightBars
              } }
          }
          view.post(applyBars)
          onDispose { view.removeCallbacks(applyBars) }
      }
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            com.futo.platformplayer.compose.onboarding.HelpGuideWindow("reorder-shuffle")
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${videos.size} · $totalDuration", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Box {
                    IconButton(onClick = { sortMenu = true }, enabled = !locked && draggedId == null) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.ascending)) },
                            leadingIcon = { if (ascending) Icon(Icons.Outlined.Check, null) }, onClick = { ascending = !ascending })
                        val options = listOf(R.string.playlist_order, R.string.sort_name) +
                            (if (videos.any { it.viewCount > 0 }) listOf(R.string.sort_popularity) else emptyList()) +
                            (if (videos.any { it.publishedAtMs > 0 }) listOf(R.string.sort_upload_date) else emptyList())
                        options.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option)) }, onClick = {
                            val sorted = when (option) {
                                R.string.sort_name -> videos.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
                                R.string.sort_popularity -> sortedRemotePlaylistVideos(videos, RemotePlaylistSortMode.Popularity, ascending)
                                R.string.sort_upload_date -> sortedRemotePlaylistVideos(videos, RemotePlaylistSortMode.UploadDate, ascending)
                                else -> videos
                            }.let { if (!ascending && option != R.string.sort_popularity && option != R.string.sort_upload_date) it.asReversed() else it }
                            orderedIds = sorted.map(VideoUiModel::id).distinct(); normalOrder = orderedIds
                            shuffleEnabled = false; sortMenu = false
                        }) }
                    }
                }
                TextButton(modifier = Modifier.testTag("reorder-confirm"), enabled = draggedId == null && !dropping, onClick = ::finish) { Text(stringResource(R.string.save)) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().testTag("playlist-reorder-list")
                        // Gesture ownership is on the stable list, not on a row that moves or
                        // leaves composition. Only the trailing handle starts a reorder.
                        .pointerInput(listState, edgePx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (locked || dropping || down.position.x < size.width - edgePx) return@awaitEachGesture
                                val row = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    down.position.y >= it.offset && down.position.y < it.offset + it.size
                                } ?: return@awaitEachGesture
                                val id = (row.key as? String)?.takeIf { it.startsWith("video:") }
                                    ?.removePrefix("video:") ?: return@awaitEachGesture
                                val sourceIndex = orderedIds.indexOf(id)
                                if (sourceIndex < 0) return@awaitEachGesture
                                slotIndex = sourceIndex
                                dragTop = row.offset.toFloat()
                                dragHeight = row.size.toFloat()
                                grabOffset = down.position.y - dragTop
                                draggedId = id
                                down.consume()
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null) { cancelDrag(); break }
                                        if (!change.pressed) { change.consume(); finishDrag(); break }
                                        dragTop = change.position.y - grabOffset
                                        change.consume()
                                        updateSlot()
                                    }
                                } finally {
                                    if (!dropping) cancelDrag()
                                }
                            }
                        },
                ) {
                    itemsIndexed(entries, key = { _, id -> id?.let { "video:$it" } ?: DROP_SLOT_KEY }) { index, id ->
                        if (id == null) {
                            Box(Modifier.fillMaxWidth().height(with(density) { dragHeight.toDp() })
                                .testTag("playlist-reorder-slot")
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), MaterialTheme.shapes.medium)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), MaterialTheme.shapes.medium))
                        } else {
                            ReorderTitleRow(
                                video = videosById[id],
                                index = index,
                                current = id == currentVideoId,
                                playing = isPlaying,
                                handleTag = "playlist-reorder-handle-$id",
                                modifier = Modifier.testTag("playlist-reorder-$index").animateItem(
                                    fadeInSpec = null, fadeOutSpec = null,
                                    placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                ),
                            )
                        }
                    }
                }
                if (isLoadingMore) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                draggedId?.let { id ->
                    ReorderTitleRow(
                        video = videosById[id],
                        index = slotIndex,
                        current = id == currentVideoId,
                        playing = isPlaying,
                        lifted = true,
                        modifier = Modifier.offset { IntOffset(0, dragTop.roundToInt()) }
                            .height(with(density) { dragHeight.toDp() }).testTag("playlist-reorder-dragged"),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .9f))
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(checked = shuffleEnabled, enabled = draggedId == null && !dropping && !locked,
                    onCheckedChange = { enabled ->
                        if (enabled) { normalOrder = orderedIds; orderedIds = shuffledReorderIds(orderedIds, currentVideoId) }
                        else orderedIds = reconcileReorderDraft(normalOrder, videos.map(VideoUiModel::id))
                        shuffleEnabled = enabled
                        shuffleScrollJob?.cancel()
                        shuffleScrollJob = scope.launch {
                            listState.animateScrollToItem(if (enabled) 0 else normalOrder.indexOf(currentVideoId).coerceAtLeast(0))
                        }
                    }, modifier = Modifier.testTag("reorder-shuffle").helpAnchor("reorder-shuffle")) {
                    Icon(Icons.Outlined.Shuffle, stringResource(R.string.shuffle_playlist))
                }
                AdaptiveButton(onClick = { onPlayOrder(reconcileReorderDraft(orderedIds, currentIds)) },
                    enabled = orderedIds.isNotEmpty() && draggedId == null && !dropping,
                    modifier = Modifier.testTag("reorder-play").helpAnchor("reorder-play")) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play_in_this_order))
                }
                IconToggleButton(checked = locked, onCheckedChange = { locked = it }, modifier = Modifier.testTag("reorder-lock")) {
                    Icon(if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, stringResource(R.string.playlist_reorder_lock))
                }
            }
        }
      }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_order_title)) },
        text = { Text(stringResource(R.string.discard_order_body)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; finish() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.discard_changes)) } },
    )
}

@Composable
private fun ReorderTitleRow(video: VideoUiModel?, index: Int, current: Boolean, playing: Boolean,
    modifier: Modifier = Modifier, handleTag: String? = null, lifted: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (lifted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = if (lifted) 8.dp else 0.dp,
    ) {
        if (video != null) MusicTrackRow(video, index, current, playing, onClick = {}, onLongClick = {}, artwork = true) {
            Box(Modifier.size(48.dp).then(handleTag?.let { Modifier.testTag(it) } ?: Modifier), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DragHandle, contentDescription = stringResource(R.string.reorder_video))
            }
        }
    }
}
