package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor

internal val SearchFieldShape = RoundedCornerShape(28.dp)

@Composable
private fun searchColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** One filled, pill-shaped search field throughout the app, without a floating label. */
@Composable
internal fun GrayjoySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchFieldShape,
    singleLine: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
    label: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    if (useLeanSearchField()) {
        BasicTextField(value = value, onValueChange = onValueChange, modifier = modifier,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = singleLine, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            decorationBox = { input -> LeanSearchDecoration(value.isEmpty(), shape, placeholder ?: label,
                leadingIcon, trailingIcon, supportingText, input) })
        return
    }
    TextField(value = value, onValueChange = onValueChange, modifier = modifier,
    shape = shape, singleLine = singleLine, placeholder = placeholder ?: label,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText,
    keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, colors = searchColors())
}

@Composable
internal fun GrayjoySearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchFieldShape,
    singleLine: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    if (useLeanSearchField()) {
        BasicTextField(value = value, onValueChange = onValueChange, modifier = modifier,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = singleLine, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            decorationBox = { input -> LeanSearchDecoration(value.text.isEmpty(), shape, placeholder,
                leadingIcon, trailingIcon, supportingText, input) })
        return
    }
    TextField(value = value, onValueChange = onValueChange, modifier = modifier,
    shape = shape, singleLine = singleLine, placeholder = placeholder,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText,
    keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, colors = searchColors())
}

@Composable
private fun useLeanSearchField() = com.futo.platformplayer.compose.rememberDevicePerformanceProfile().isLowEnd &&
    compactUi() && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.25f

@Composable
private fun LeanSearchDecoration(empty: Boolean, shape: Shape, placeholder: (@Composable () -> Unit)?,
    leading: (@Composable () -> Unit)?, trailing: (@Composable () -> Unit)?,
    supporting: (@Composable () -> Unit)?, input: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (empty && placeholder != null) androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge) { placeholder() }
                input()
            }
            trailing?.invoke()
        }
        if (supporting != null) Box(Modifier.padding(start = 16.dp, top = 4.dp)) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodySmall) { supporting() }
        }
    }
}
