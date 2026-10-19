package com.futo.platformplayer.compose.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

data class YoutubeAutoplayUiState(val title: String, val seconds: Int)
internal data class YoutubeAutoplayControls(val state: YoutubeAutoplayUiState? = null, val cancel: () -> Unit = {})
internal val LocalYoutubeAutoplay = compositionLocalOf { YoutubeAutoplayControls() }

internal suspend fun youtubeAutoplayCountdown(valid: () -> Boolean, tick: (Int) -> Unit,
    wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }): Boolean {
    for (seconds in 10 downTo 1) {
        if (!valid()) return false
        tick(seconds)
        wait(1_000L)
    }
    return valid()
}

internal fun youtubeAutoplayCandidates(videos: List<VideoUiModel>, seen: Set<String>, now: Long = System.currentTimeMillis()): List<VideoUiModel> =
    videos.filter { it.sourceId == "youtube" && it.id !in seen && it.isAvailable && !it.isLive && !it.isShort && it.scheduledStartAtMs <= now }
        .distinctBy(VideoUiModel::id)

@Composable
internal fun YoutubeAutoplayPrompt(modifier: Modifier = Modifier) {
    val controls = LocalYoutubeAutoplay.current
    val state = controls.state ?: return
    Surface(modifier.widthIn(max = 340.dp).padding(12.dp).testTag("youtube-autoplay-countdown"),
        shape = MaterialTheme.shapes.large, color = Color.Black.copy(alpha = 0.94f), contentColor = Color.White) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.youtube_autoplay_countdown, state.seconds), style = MaterialTheme.typography.titleSmall)
            Text(state.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = controls.cancel, modifier = Modifier.testTag("youtube-autoplay-cancel")) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}
