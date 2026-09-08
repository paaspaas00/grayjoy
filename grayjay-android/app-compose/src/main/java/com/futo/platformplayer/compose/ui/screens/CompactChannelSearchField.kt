package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

@Composable
internal fun CompactChannelSearchField(value: String, onValueChange: (String) -> Unit, count: Int, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    val searchLabel = stringResource(R.string.search_channel_videos)
    val countLabel = pluralStringResource(R.plurals.video_count, count, count)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); keyboard?.hide() }),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = searchLabel },
        decorationBox = { input ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                    .then(if (focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    if (value.isEmpty()) Text(stringResource(R.string.nav_search), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    input()
                }
                Text(count.toString(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = countLabel })
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear_video_search), modifier = Modifier.size(18.dp))
                }
            }
        },
    )
}
