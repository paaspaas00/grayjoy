package com.futo.platformplayer.compose.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

/** Small, theme-aware vector diagrams remain sharp at every display density. */
@Composable
internal fun OneHandedPlayerChoice(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (oneHanded in listOf(false, true)) {
            val selected = enabled == oneHanded
            Surface(Modifier.weight(1f).testTag(if (oneHanded) "setup-player-one-handed" else "setup-player-standard")
                .clip(MaterialTheme.shapes.large)
                .selectable(selected, role = Role.RadioButton, onClick = { onChange(oneHanded) }),
                shape = MaterialTheme.shapes.large,
                color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(152.dp)) {
                        val h = size.height - 8.dp.toPx()
                        val w = h * .55f
                        val left = (size.width - w) / 2
                        val top = 4.dp.toPx()
                        drawRoundRect(colors.surface, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()))
                        drawRoundRect(colors.outline, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        drawRoundRect(colors.outlineVariant, Offset(left + w * .35f, top + h * .04f), Size(w * .3f, h * .025f), CornerRadius(3.dp.toPx()))
                        val videoY = top + h * if (oneHanded) .31f else .16f
                        val videoH = h * .28f
                        drawRoundRect(colors.primaryContainer, Offset(left + w * .08f, videoY), Size(w * .84f, videoH), CornerRadius(4.dp.toPx()))
                        val center = Offset(left + w / 2, videoY + videoH * .58f)
                        drawPath(Path().apply {
                            moveTo(center.x - 4.dp.toPx(), center.y - 6.dp.toPx())
                            lineTo(center.x + 6.dp.toPx(), center.y)
                            lineTo(center.x - 4.dp.toPx(), center.y + 6.dp.toPx()); close()
                        }, colors.onPrimaryContainer)
                        val titleY = top + h * if (oneHanded) .16f else .49f
                        drawRoundRect(colors.onSurface, Offset(left + w * .1f, titleY), Size(w * .68f, h * .028f), CornerRadius(2.dp.toPx()))
                        drawRoundRect(colors.onSurfaceVariant, Offset(left + w * .1f, titleY + h * .055f), Size(w * .48f, h * .018f), CornerRadius(2.dp.toPx()))
                        repeat(3) { row -> drawRoundRect(colors.outlineVariant, Offset(left + w * .1f, top + h * (.7f + row * .07f)),
                            Size(w * .8f, h * .028f), CornerRadius(2.dp.toPx())) }
                        if (oneHanded) {
                            val touch = Offset(left + w * .84f, videoY + videoH * .7f)
                            drawCircle(colors.primary.copy(alpha = .16f), 14.dp.toPx(), touch)
                            drawCircle(colors.primary, 6.dp.toPx(), touch, style = Stroke(2.dp.toPx()))
                        }
                    }
                    Text(stringResource(if (oneHanded) R.string.setup_player_one_handed else R.string.setup_player_standard),
                        style = MaterialTheme.typography.titleSmall)
                    RadioButton(selected, onClick = null)
                }
            }
        }
    }
}
