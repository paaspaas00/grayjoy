package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

@Composable
internal fun CompactSettingRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    var titleOverflows by remember(title) { mutableStateOf(false) }
    var descriptionOverflows by remember(description) { mutableStateOf(false) }
    var showDetails by rememberSaveable(title) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                onTextLayout = { titleOverflows = it.hasVisualOverflow })
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                onTextLayout = { descriptionOverflows = it.hasVisualOverflow })
        }
        if (titleOverflows || descriptionOverflows) {
            IconButton(onClick = { showDetails = true }) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.details))
            }
        }
        trailing()
    }
    if (showDetails) {
        AlertDialog(onDismissRequest = { showDetails = false }, title = { Text(title) },
            text = { Text(description, modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.ok)) } })
    }
}
