package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Keep the established two-button layout when it fits; stack instead of clipping labels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveActionRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable FlowRowScope.() -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (maxWidth < 300.dp * fontScale) 1 else 2,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

internal fun videoCardThumbnailWidth(screenWidthDp: Int, compact: Boolean): Float {
    val preferred = if (compact) 148f else 184f
    return if (screenWidthDp >= 390) preferred else minOf(preferred, (screenWidthDp - 52f).coerceAtLeast(160f) * 0.44f)
}
