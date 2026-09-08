package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile

/** A small bounded window keeps reverse scrolling warm without retaining entire feeds. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberContentListState(): LazyListState {
    val lowEnd = rememberDevicePerformanceProfile().isLowEnd
    val cacheWindow = remember(lowEnd) {
        LazyLayoutCacheWindow(
            ahead = if (lowEnd) 160.dp else 320.dp,
            behind = if (lowEnd) 80.dp else 184.dp,
        )
    }
    return rememberLazyListState(cacheWindow = cacheWindow)
}

/** Preferences have small, image-free rows: retain a larger bounded window for fast flings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberSettingsListState(): LazyListState {
    val lowEnd = rememberDevicePerformanceProfile().isLowEnd
    val cacheWindow = remember(lowEnd) {
        LazyLayoutCacheWindow(
            ahead = if (lowEnd) 720.dp else 1440.dp,
            behind = if (lowEnd) 480.dp else 960.dp,
        )
    }
    return rememberLazyListState(cacheWindow = cacheWindow)
}
