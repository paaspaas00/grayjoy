package com.futo.platformplayer.compose.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.sourceStringResource
import kotlin.math.roundToInt

internal data class HelpStep(val text: Int, val target: String)
@Composable
private fun helpStepText(id: Int): String = if (id == R.string.help_choose_channel_filter) {
    stringResource(id, sourceStringResource(R.string.creators))
} else stringResource(id)
internal enum class HelpTopic(val title: Int, val description: Int, val steps: List<HelpStep>) {
    Playlist(R.string.help_playlist, R.string.help_playlist_body, listOf(
        HelpStep(R.string.help_find_first, "search-field"),
        HelpStep(R.string.help_hold_video, "video-card"),
        HelpStep(R.string.help_add_playlist, "video-action-playlist"),
        HelpStep(R.string.help_name_playlist, "playlist-editor"))),
    Music(R.string.help_music, R.string.help_music_body, listOf(
        HelpStep(R.string.help_find_music, "search-field"),
        HelpStep(R.string.help_play_result, "video-card"),
        HelpStep(R.string.help_music_playing, "player-actions"))),
    Random(R.string.help_random, R.string.help_random_body, listOf(
        HelpStep(R.string.help_choose_playlist, "playlist-row"),
        HelpStep(R.string.help_shuffle_playlist, "playlist-shuffle"),
        HelpStep(R.string.help_start_playlist, "playlist-play-all"))),
    Album(R.string.help_album, R.string.help_album_body, listOf(
        HelpStep(R.string.help_find_album, "search-field"),
        HelpStep(R.string.help_album_filter, "search-filter-playlists"),
        HelpStep(R.string.help_open_album, "playlist-row"),
        HelpStep(R.string.help_start_album, "playlist-play-all"))),
    Download(R.string.help_download, R.string.help_download_body, listOf(
        HelpStep(R.string.help_find_download, "search-field"),
        HelpStep(R.string.help_play_result, "video-card"),
        HelpStep(R.string.help_download_button, "toggle-download"),
        HelpStep(R.string.help_download_format, "download-format-audio"))),
    Follow(R.string.help_follow, R.string.help_follow_body, listOf(
        HelpStep(R.string.help_find_channel, "search-field"),
        HelpStep(R.string.help_choose_channel_filter, "search-filter-creators"),
        HelpStep(R.string.help_open_channel_result, "channel-result"),
        HelpStep(R.string.help_follow_button, "channel-follow"))),
    Profiles(R.string.help_profiles, R.string.help_profiles_body, listOf(
        HelpStep(R.string.help_profiles_open, "profile-menu"),
        HelpStep(R.string.help_profiles_choose, "profile-picker"))),
}

@Stable
internal class HelpGuideState {
    var visible by mutableStateOf(false)
    var topic by mutableStateOf<HelpTopic?>(null)
    var step by mutableIntStateOf(0)
    var session by mutableIntStateOf(0)
    var collapsed by mutableStateOf(false)
    var ready by mutableStateOf(false)
    val windows = mutableStateListOf<Any>()
    private val anchors = mutableStateMapOf<Any, Pair<String, Rect>>()
    val current: HelpStep? get() = topic?.steps?.getOrNull(step)
    fun start(value: HelpTopic) {
        topic = value; step = 0; session++; visible = false; collapsed = false; anchors.clear()
    }
    fun stop() { topic = null; anchors.clear(); collapsed = false }
    fun register(owner: Any, name: String, rect: Rect) {
        if (rect.width > 8 && rect.height > 16) anchors[owner] = name to rect
        else anchors.remove(owner)
    }
    fun remove(owner: Any) { anchors.remove(owner) }
    fun bounds(name: String): Rect? = anchors.values.filter { it.first == name }.map { it.second }
        .minByOrNull { it.top }
    fun next() {
        val last = topic?.steps?.lastIndex ?: return
        if (step < last) { step++; collapsed = false } else stop()
    }
    fun reached(target: String) {
        val index = topic?.steps?.indexOfFirst { it.target == target } ?: return
        if (index > step) { step = index; collapsed = false }
    }
}

internal val LocalHelpGuide = staticCompositionLocalOf<HelpGuideState?> { null }

/** Reserve space inside modal windows instead of covering their fields or the soft keyboard. */
@Composable
internal fun HelpGuideWindow(target: String) {
    val guide = LocalHelpGuide.current
    if (guide?.topic != null) {
        LaunchedEffect(guide.session, target) { guide.reached(target) }
        val owner = remember { Any() }
        DisposableEffect(guide, owner) {
            guide.windows.add(owner)
            onDispose { guide.windows.remove(owner) }
        }
        if (guide.windows.lastOrNull() === owner) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("help-bubble")) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.help_step, guide.step + 1, guide.topic!!.steps.size), Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { guide.collapsed = !guide.collapsed }, modifier = Modifier.size(40.dp)) {
                            Icon(if (guide.collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                stringResource(if (guide.collapsed) R.string.help_expand else R.string.help_collapse))
                        }
                        IconButton(onClick = guide::stop, modifier = Modifier.size(40.dp).testTag("help-stop")) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.help_end))
                        }
                        TextButton(onClick = guide::next, enabled = guide.ready, modifier = Modifier.testTag("help-next")) {
                            Text(stringResource(if (guide.step == guide.topic!!.steps.lastIndex) R.string.help_finish else R.string.setup_next))
                        }
                    }
                    if (!guide.collapsed) guide.current?.let {
                        Text(helpStepText(it.text), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberHelpGuide(): HelpGuideState = rememberSaveable(saver = listSaver(
    save = { listOf(it.visible, it.topic?.name.orEmpty(), it.step, it.session, it.collapsed) },
    restore = { saved -> HelpGuideState().apply {
        visible = saved[0] as Boolean
        topic = HelpTopic.entries.firstOrNull { it.name == saved[1] }
        step = (saved[2] as Int).coerceIn(0, topic?.steps?.lastIndex ?: 0)
        session = saved[3] as Int
        collapsed = saved[4] as Boolean
    } },
)) { HelpGuideState() }

/** Observe only during a tour; ordinary scrolling does not maintain geometry for the help UI. */
internal fun Modifier.helpAnchor(name: String): Modifier = composed {
    val guide = LocalHelpGuide.current
    if (guide?.topic == null) this else {
        val owner = remember { Any() }
        DisposableEffect(guide, owner) { onDispose { guide.remove(owner) } }
        this.onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) guide.register(owner, name, coordinates.boundsInWindow())
        }
    }
}

@Composable
internal fun HelpButton(
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    beforeOpen: () -> Unit = {},
) {
    val guide = LocalHelpGuide.current ?: return
    IconButton(onClick = { beforeOpen(); guide.stop(); guide.visible = true }, modifier = Modifier.testTag("open-help")) {
        Icon(Icons.Outlined.HelpOutline, stringResource(R.string.help_title), tint = tint)
    }
}

@Composable
internal fun HelpScreen(guide: HelpGuideState, sourceProfilesEnabled: Boolean = false, canUseYoutube: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { guide.visible = false }, properties =
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.help_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { guide.visible = false }) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                    }
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("help-topics")) {
                        item { Text(stringResource(R.string.help_intro), style = MaterialTheme.typography.bodyLarge) }
                        items(HelpTopic.entries.filter { (it != HelpTopic.Profiles || sourceProfilesEnabled) &&
                            (it != HelpTopic.Follow || canUseYoutube) }, key = { it.name }) { topic ->
                            val open = expanded == topic.name
                            OutlinedCard(onClick = { expanded = if (open) null else topic.name },
                                modifier = Modifier.fillMaxWidth().testTag("help-topic-${topic.name}")) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(topic.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            stringResource(if (open) R.string.help_collapse else R.string.help_expand))
                                    }
                                    if (open) {
                                        Text(stringResource(topic.description), style = MaterialTheme.typography.bodyLarge)
                                        Button(onClick = { guide.start(topic) }, modifier = Modifier.testTag("help-start-${topic.name}")) {
                                            Text(stringResource(R.string.help_show))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Picks a free side of the target and clamps to the usable window (including the keyboard). */
internal fun helpBubblePosition(target: Rect?, window: IntSize, bubble: IntSize,
    topInset: Int, bottomInset: Int, margin: Int, avoid: Rect? = null): IntOffset {
    val left = margin.coerceAtMost((window.width - bubble.width).coerceAtLeast(0))
    val maxY = (window.height - bottomInset - margin - bubble.height).coerceAtLeast(topInset)
    val above = target?.top?.roundToInt()?.minus(margin + bubble.height)
    val below = target?.bottom?.roundToInt()?.plus(margin)
    val y = when {
        above != null && above >= topInset + margin -> above
        below != null && below <= maxY -> maxOf(below, avoid?.bottom?.roundToInt()?.plus(margin) ?: below).coerceAtMost(maxY)
        target != null && target.center.y > window.height / 2 -> topInset + margin
        else -> maxY
    }.coerceIn(topInset, maxY)
    return IntOffset(((target?.center?.x ?: (window.width / 2f)) - bubble.width / 2f).roundToInt()
        .coerceIn(left, (window.width - margin - bubble.width).coerceAtLeast(left)), y)
}

@Composable
internal fun HelpBubble(guide: HelpGuideState, ready: Boolean, windowOwner: Any? = null) {
    if (guide.windows.lastOrNull() !== windowOwner) return
    val topic = guide.topic ?: return
    val step = guide.current ?: return
    val density = LocalDensity.current
    val context = LocalContext.current
    val target = guide.bounds(step.target)
    val avoid = if (step.target == "search-field") guide.bounds("search-type-tabs") else null
    val top = WindowInsets.safeDrawing.getTop(density)
    val bottom = maxOf(WindowInsets.safeDrawing.getBottom(density), WindowInsets.ime.getBottom(density))
    val margin = with(density) { 12.dp.roundToPx() }
    val fullHeight = if (android.os.Build.VERSION.SDK_INT >= 30) {
        context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds.height()
    } else context.resources.displayMetrics.heightPixels
    val position = remember(target, avoid, top, bottom, margin, fullHeight) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            // Popup can already receive the IME-resized visible window. Do not subtract the
            // keyboard twice: that would push the tip back over the search field.
            helpBubblePosition(target, windowSize, popupContentSize, top,
                remainingHelpInset(fullHeight, windowSize.height, bottom), margin, avoid)
    } }
    BackHandler { guide.stop() }
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false,
        dismissOnClickOutside = false, dismissOnBackPress = false)) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .4f)),
            modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(.9f).testTag("help-bubble")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.setup_step, guide.step + 1, topic.steps.size),
                        Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { guide.collapsed = !guide.collapsed }, modifier = Modifier.size(40.dp)) {
                        Icon(if (guide.collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            stringResource(if (guide.collapsed) R.string.help_expand else R.string.help_collapse))
                    }
                    IconButton(onClick = guide::stop, modifier = Modifier.size(40.dp).testTag("help-stop")) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.help_end))
                    }
                }
                if (!guide.collapsed) {
                    Text(helpStepText(step.text), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
                    if (!ready) Text(stringResource(R.string.help_wait), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (guide.step > 0) TextButton(onClick = { guide.step-- }) { Text(stringResource(R.string.back)) }
                        Button(onClick = guide::next, enabled = ready, modifier = Modifier.testTag("help-next")) {
                            Text(stringResource(if (guide.step == topic.steps.lastIndex) R.string.help_finish else R.string.setup_next))
                        }
                    }
                }
            }
        }
    }
}

internal fun remainingHelpInset(fullHeight: Int, visibleHeight: Int, inset: Int): Int =
    (inset - (fullHeight - visibleHeight).coerceAtLeast(0)).coerceAtLeast(0)
