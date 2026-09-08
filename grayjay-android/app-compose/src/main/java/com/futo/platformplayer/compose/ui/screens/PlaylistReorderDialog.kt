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
) {
    var orderedIds by rememberSaveable { mutableStateOf(videos.map(VideoUiModel::id).distinct()) }
    val videosById = remember(videos) { videos.associateBy(VideoUiModel::id) }
    val listState = rememberLazyListState()
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

    fun cancelDrag() {
        dropJob?.cancel()
        draggedId = null
        dropping = false
    }
    LaunchedEffect(videos.map(VideoUiModel::id)) {
        orderedIds = reconcileReorderDraft(orderedIds, videos.map(VideoUiModel::id))
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reorder_playlist)) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).clipToBounds()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().testTag("playlist-reorder-list")
                        // Gesture ownership is on the stable list, not on a row that moves or
                        // leaves composition. Only the trailing handle starts a reorder.
                        .pointerInput(listState, edgePx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (dropping || down.position.x < size.width - edgePx) return@awaitEachGesture
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
                                title = videosById[id]?.title.orEmpty(),
                                handleTag = "playlist-reorder-handle-$id",
                                modifier = Modifier.testTag("playlist-reorder-$index").animateItem(
                                    fadeInSpec = null, fadeOutSpec = null,
                                    placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                ),
                            )
                        }
                    }
                }
                draggedId?.let { id ->
                    ReorderTitleRow(
                        title = videosById[id]?.title.orEmpty(),
                        lifted = true,
                        modifier = Modifier.offset { IntOffset(0, dragTop.roundToInt()) }
                            .height(with(density) { dragHeight.toDp() }).testTag("playlist-reorder-dragged"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = draggedId == null && !dropping,
                onClick = { if (draggedId == null && !dropping) onConfirm(reconcileReorderDraft(orderedIds, videos.map(VideoUiModel::id))) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ReorderTitleRow(title: String, modifier: Modifier = Modifier, handleTag: String? = null, lifted: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (lifted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (lifted) 8.dp else 0.dp,
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f).padding(vertical = 8.dp), maxLines = 3,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.size(48.dp).then(handleTag?.let { Modifier.testTag(it) } ?: Modifier), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DragHandle, contentDescription = stringResource(R.string.reorder_video))
            }
        }
    }
}
