package com.futo.platformplayer.compose.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.sourceCreatorFallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class CreatorSheetPhase { Preview, Opening, Dismissing }
internal data class CreatorSheetRequest(val channel: ChannelUiModel, val following: Boolean,
    val toggleFollowing: () -> Unit, val openChannel: () -> Unit,
    val phase: CreatorSheetPhase = CreatorSheetPhase.Preview)

@Stable
internal class CreatorSheetController(private val preload: (ChannelUiModel) -> Unit) {
    var request by mutableStateOf<CreatorSheetRequest?>(null)
        private set
    fun show(channel: ChannelUiModel, following: Boolean, toggleFollowing: () -> Unit, openChannel: () -> Unit) {
        request = CreatorSheetRequest(channel, following, toggleFollowing, openChannel)
        preload(channel)
    }
    fun open() {
        val current = request?.takeIf { it.phase == CreatorSheetPhase.Preview } ?: return
        request = current.copy(phase = CreatorSheetPhase.Opening)
        current.openChannel()
    }
    fun dismiss() { request = request?.copy(phase = CreatorSheetPhase.Dismissing) }
    fun finish(expected: CreatorSheetRequest) { if (request === expected) request = null }
    fun toggleFollow() {
        val current = request ?: return
        current.toggleFollowing()
        request = current.copy(following = !current.following)
    }
}

internal val LocalCreatorSheet = staticCompositionLocalOf<CreatorSheetController?> { null }
private val LocalCreatorSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

@Composable
internal fun Modifier.creatorSharedElement(channelId: String, part: String, fromSheet: Boolean = false): Modifier {
    val request = LocalCreatorSheet.current?.request ?: return this
    if (request.channel.id != channelId) return this
    val scope = LocalCreatorSharedScope.current ?: return this
    return with(scope) {
        val shared = this@creatorSharedElement.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState("creator-sheet:$channelId:$part"),
            visible = if (fromSheet) request.phase != CreatorSheetPhase.Opening else request.phase == CreatorSheetPhase.Opening,
            boundsTransform = { _, _ -> tween(320, easing = FastOutSlowInEasing) },
            zIndexInOverlay = 100f)
        if (part == "name" || part == "metadata" || part == "description") shared.skipToLookaheadSize()
        else shared
    }
}

@Composable
internal fun SharedCreatorText(channelId: String, part: String, text: String, modifier: Modifier = Modifier,
    fromSheet: Boolean = false, style: TextStyle, color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Center, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, onTextLayout: (TextLayoutResult) -> Unit = {}) {
    Text(text, modifier.creatorSharedElement(channelId, part, fromSheet), color = color,
        style = style, textAlign = textAlign, maxLines = maxLines, overflow = overflow, onTextLayout = onTextLayout)
}

@Composable
internal fun CreatorSheetTransitionHost(profileId: String, preload: (ChannelUiModel) -> Unit,
    resolvedChannel: ChannelUiModel?, loading: Boolean, content: @Composable () -> Unit) {
    val latestPreload by rememberUpdatedState(preload)
    val controller = remember(profileId) { CreatorSheetController { latestPreload(it) } }
    val latestContent = rememberUpdatedState(content)
    val movableBody = remember { movableContentOf { latestContent.value() } }
    var sharedLayoutEnabled by remember { mutableStateOf(false) }
    // Do not put every first-frame list through lookahead before a shared transition is used.
    // Movable content keeps navigation, focus, list positions and the player instance intact.
    CompositionLocalProvider(LocalCreatorSheet provides controller) {
        if (sharedLayoutEnabled || controller.request != null) {
            SideEffect { sharedLayoutEnabled = true }
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalCreatorSharedScope provides this) {
                    movableBody()
                    controller.request?.let { request ->
                        val channel = resolvedChannel?.takeIf { it.id == request.channel.id } ?: request.channel
                        CreatorTransitionSheet(controller, request, channel, loading)
                    }
                }
            }
        } else Box(Modifier.fillMaxSize()) { movableBody() }
    }
}

@Composable
private fun CreatorTransitionSheet(controller: CreatorSheetController, request: CreatorSheetRequest, channel: ChannelUiModel, loading: Boolean) {
    var drag by remember(channel.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val appearing = remember(channel.id) { Animatable(0f) }
    val pageCover = remember(channel.id) { Animatable(1f) }
    LaunchedEffect(channel.id) { appearing.animateTo(1f, tween(180)) }
    val leaving by animateFloatAsState(if (request.phase == CreatorSheetPhase.Preview) 0f else 1f, tween(200))
    LaunchedEffect(request.phase) {
        if (request.phase == CreatorSheetPhase.Opening) {
            delay(190)
            pageCover.animateTo(0f, tween(170))
            controller.finish(request)
        } else if (request.phase == CreatorSheetPhase.Dismissing) { delay(220); controller.finish(request) }
    }
    BackHandler { if (request.phase == CreatorSheetPhase.Preview) controller.dismiss() }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("creator-sheet-overlay")) {
        val compact = maxHeight < 480.dp
        val compactContent = com.futo.platformplayer.compose.rememberDevicePerformanceProfile().compactContent
        val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (request.phase == CreatorSheetPhase.Opening) pageCover.value else appearing.value * (1f - leaving)
            }
            .background(if (request.phase == CreatorSheetPhase.Opening) MaterialTheme.colorScheme.background else Color.Black.copy(alpha = .48f))
            .clickable { if (request.phase == CreatorSheetPhase.Preview) controller.dismiss() })
        Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().widthIn(max = 640.dp).fillMaxWidth()
            .offset { IntOffset(0, (drag + (1f - appearing.value) * 64f + if (request.phase == CreatorSheetPhase.Dismissing) leaving * 200f else 0f).roundToInt()) }
            .graphicsLayer { alpha = 1f - leaving }
            .pointerInput(channel.id, request.phase) {
                if (request.phase == CreatorSheetPhase.Preview) detectVerticalDragGestures(
                    onDragStart = { settleJob?.cancel() },
                    onVerticalDrag = { change, amount -> change.consume(); drag = (drag + amount).coerceIn(-size.height * .7f, size.height.toFloat()) },
                    onDragEnd = {
                        when { drag < -threshold -> controller.open()
                            drag > threshold -> controller.dismiss()
                            else -> settleJob = scope.launch { animate(drag, 0f, animationSpec = tween(180)) { value, _ -> drag = value } } }
                    }, onDragCancel = { settleJob = scope.launch { animate(drag, 0f, animationSpec = tween(180)) { value, _ -> drag = value } } })
            }.testTag("creator-transition-sheet"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                Box(Modifier.size(36.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), RoundedCornerShape(2.dp)))
                ChannelAvatarImage(channel.name, channel.thumbnailUrl,
                    Modifier.creatorSharedElement(channel.id, "avatar", true).size(if (compact) 56.dp else 88.dp))
                SharedCreatorText(channel.id, "name", channel.name, fromSheet = true,
                    style = if (compactContent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                SharedCreatorText(channel.id, "metadata", "${channel.source} • ${sourceCreatorFallback(channel.followerCount)}", fromSheet = true,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                if (channel.description.isNotBlank()) SharedCreatorText(channel.id, "description", channel.description, fromSheet = true,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                else if (loading) ShimmerBlock(Modifier.fillMaxWidth(.85f).height(if (compact) 24.dp else 48.dp)
                    .testTag("creator-description-skeleton"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdaptiveButton(onClick = controller::toggleFollow,
                        modifier = Modifier.weight(1f).creatorSharedElement(channel.id, "follow", true)) {
                        Text(stringResource(if (request.following) R.string.following else R.string.follow))
                    }
                    AdaptiveButton(onClick = controller::open, modifier = Modifier.weight(1f)
                        .graphicsLayer { alpha = if (request.phase == CreatorSheetPhase.Opening) 0f else 1f }.testTag("open-creator-channel")) {
                        Text(stringResource(R.string.view_channel))
                    }
                }
            }
        }
    }
}
