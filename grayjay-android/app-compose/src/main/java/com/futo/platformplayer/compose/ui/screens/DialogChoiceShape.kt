package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

internal fun dialogChoiceShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) 16.dp else 4.dp,
    topEnd = if (index == 0) 16.dp else 4.dp,
    bottomStart = if (index == count - 1) 16.dp else 4.dp,
    bottomEnd = if (index == count - 1) 16.dp else 4.dp,
)

@Composable
internal fun Modifier.dialogChoiceRow(index: Int, count: Int): Modifier =
    clip(dialogChoiceShape(index, count)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
