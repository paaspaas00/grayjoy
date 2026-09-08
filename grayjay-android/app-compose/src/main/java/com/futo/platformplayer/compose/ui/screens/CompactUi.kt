package com.futo.platformplayer.compose.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

internal fun useCompactUi(widthDp: Int, heightDp: Int, fontScale: Float): Boolean =
    widthDp < 600 && (widthDp < 390 || heightDp < 650 || fontScale >= 1.25f)

@Composable
internal fun compactUi(): Boolean {
    val configuration = LocalConfiguration.current
    return useCompactUi(configuration.screenWidthDp, configuration.screenHeightDp, LocalDensity.current.fontScale)
}
