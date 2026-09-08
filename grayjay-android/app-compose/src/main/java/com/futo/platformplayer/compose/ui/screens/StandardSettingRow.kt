package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight

/** Fixed-slot preference row: one measurement per slot, no intrinsic/subcompose pass. */
@Composable
internal fun StandardSettingRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        content = {
            Box { leading() }
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box { trailing() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leadingPlaceable = measurables[0].measure(loose)
        val trailingPlaceable = measurables[2].measure(loose)
        val gap = 16.dp.roundToPx()
        val textWidth = (constraints.maxWidth - leadingPlaceable.width - trailingPlaceable.width - gap * 2).coerceAtLeast(0)
        val text = measurables[1].measure(loose.copy(minWidth = textWidth, maxWidth = textWidth))
        val threeLine = text.height > 48.dp.roundToPx()
        val verticalPadding = (if (threeLine) 12.dp else 8.dp).roundToPx()
        val height = constraints.constrainHeight(maxOf(
            (if (threeLine) 88.dp else 72.dp).roundToPx(),
            maxOf(text.height, leadingPlaceable.height, trailingPlaceable.height) + verticalPadding * 2,
        ))
        layout(constraints.maxWidth, height) {
            fun y(childHeight: Int) = if (threeLine) verticalPadding else (height - childHeight) / 2
            leadingPlaceable.placeRelative(0, y(leadingPlaceable.height))
            text.placeRelative(leadingPlaceable.width + gap, y(text.height))
            trailingPlaceable.placeRelative(constraints.maxWidth - trailingPlaceable.width, y(trailingPlaceable.height))
        }
    }
}
