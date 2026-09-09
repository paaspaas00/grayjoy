package com.futo.platformplayer.compose.ui

import android.text.format.DateUtils
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

internal val LocalDisplayClock = staticCompositionLocalOf<State<Long>> { mutableLongStateOf(System.currentTimeMillis()) }

@Composable internal fun rememberDisplayClock(): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000L - value % 60_000L)
            }
        }
    }
}

internal fun refreshedVideoMetadata(video: VideoUiModel, original: String, now: Long): String {
    if (original != video.metadata || video.publishedAtMs <= 0L) return original
    val prefix = when {
        original.contains(" • ") -> original.substringBeforeLast(" • ")
        original.contains(" · ") -> original.substringBeforeLast(" · ")
        else -> original.takeIf { video.viewCount > 0L }.orEmpty()
    }
    val age = DateUtils.getRelativeTimeSpanString(video.publishedAtMs, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    return listOf(prefix, age).filter(String::isNotBlank).joinToString(" • ")
}
