package com.futo.platformplayer.compose.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

@Composable
internal fun HandednessChoice(leftHanded: Boolean, onChange: (Boolean) -> Unit, active: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true, false).forEach { left ->
            val selected = leftHanded == left
            val fill by animateColorAsState(
                if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                tween(180), label = "hand-choice-fill")
            val shape = RoundedCornerShape(24.dp)
            Surface(Modifier.weight(1f).clip(shape)
                .selectable(selected, role = Role.RadioButton, onClick = { onChange(left) })
                .testTag(if (left) "choose-left-hand" else "choose-right-hand"),
                shape = shape, color = fill,
                border = BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) colors.primary else colors.outlineVariant)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        HandednessArtwork(left, selected, active && selected,
                            Modifier.widthIn(max = 144.dp).fillMaxWidth().aspectRatio(160f / 164f))
                        RadioButton(selected, onClick = null,
                            modifier = Modifier.align(Alignment.TopEnd).size(22.dp))
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 36.dp),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(if (left) R.string.hand_left else R.string.hand_right),
                            maxLines = 2, style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = if (selected) colors.onPrimaryContainer else colors.onSurface)
                    }
                }
            }
        }
    }
}
