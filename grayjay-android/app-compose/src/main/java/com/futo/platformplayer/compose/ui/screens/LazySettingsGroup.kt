package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile

internal class SettingsGroupScope {
    internal data class Row(val key: String, val type: String, val content: @Composable () -> Unit)
    internal val rows = mutableListOf<Row>()
    fun setting(key: String, type: String, content: @Composable () -> Unit) {
        rows += Row(key, type, content)
    }
}

/** Each preference is independently prefetched and reused, while keeping the grouped appearance. */
internal fun LazyListScope.settingsGroup(
    key: String,
    titleRes: Int?,
    gap: Dp,
    content: SettingsGroupScope.() -> Unit,
) {
    if (titleRes != null) item(key = "$key-heading", contentType = "settings-heading") {
        Text(
            stringResource(titleRes),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = if (compactUi()) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
        )
    }
    val rows = SettingsGroupScope().apply(content).rows
    rows.forEachIndexed { index, row ->
        item(key = row.key, contentType = row.type) {
            val lowEnd = rememberDevicePerformanceProfile().isLowEnd
            val corner = CornerSize(0.dp)
            val shape = if (lowEnd) RectangleShape else MaterialTheme.shapes.medium.copy(
                topStart = if (index == 0) MaterialTheme.shapes.medium.topStart else corner,
                topEnd = if (index == 0) MaterialTheme.shapes.medium.topEnd else corner,
                bottomStart = if (index == rows.lastIndex) MaterialTheme.shapes.medium.bottomStart else corner,
                bottomEnd = if (index == rows.lastIndex) MaterialTheme.shapes.medium.bottomEnd else corner,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = if (index == rows.lastIndex) gap else 0.dp),
                shape = shape,
                color = if (compactUi() || lowEnd) CardDefaults.cardColors().containerColor
                    else MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    row.content()
                    if (index != rows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
