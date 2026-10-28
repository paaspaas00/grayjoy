package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futo.platformplayer.compose.rememberDevicePerformanceProfile

@Composable
internal fun compactControls(): Boolean = rememberDevicePerformanceProfile().isLowEnd &&
    LocalConfiguration.current.smallestScreenWidthDp < 600 && LocalDensity.current.fontScale < 1.25f

/** Reserve a comfortable touch slot without crushing controls with an explicit caller height. */
private fun compactControlModifier(modifier: Modifier, visualDp: Int, gapDp: Int): Modifier = modifier.layout { measurable, constraints ->
    val outerHeight = maxOf(48.dp.roundToPx(), constraints.minHeight).coerceAtMost(constraints.maxHeight)
    val visualHeight = maxOf(visualDp.dp.roundToPx(), outerHeight - gapDp.dp.roundToPx()).coerceAtMost(outerHeight)
    val child = measurable.measure(constraints.copy(minHeight = visualHeight, maxHeight = visualHeight))
    layout(child.width, outerHeight) { child.placeRelative(0, (outerHeight - child.height) / 2) }
}

@Composable
internal fun AdaptiveFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape, colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null) {
    val compact = compactControls()
    FilterChip(selected, onClick, label = {
        if (compact) ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)) { label() }
        else label()
    }, modifier = if (compact) compactControlModifier(modifier, 28, 20) else modifier,
        enabled = enabled, leadingIcon = leadingIcon?.let { { Box(if (compact) Modifier.size(16.dp) else Modifier) { it() } } },
        trailingIcon = trailingIcon?.let { { Box(if (compact) Modifier.size(16.dp) else Modifier) { it() } } },
        shape = shape, colors = colors, elevation = elevation, border = border, interactionSource = interactionSource)
}

@Composable
internal fun AdaptiveAssistChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(), elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled), interactionSource: MutableInteractionSource? = null) {
    val compact = compactControls()
    AssistChip(onClick, label = {
        if (compact) ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)) { label() }
        else label()
    }, modifier = if (compact) compactControlModifier(modifier, 28, 20) else modifier, enabled = enabled,
        leadingIcon = leadingIcon?.let { { Box(if (compact) Modifier.size(16.dp) else Modifier) { it() } } },
        trailingIcon = trailingIcon?.let { { Box(if (compact) Modifier.size(16.dp) else Modifier) { it() } } },
        shape = shape, colors = colors, elevation = elevation, border = border, interactionSource = interactionSource)
}

@Composable
internal fun AdaptiveSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null, enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(), interactionSource: MutableInteractionSource? = null) {
    if (!compactControls()) {
        Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)
        return
    }
    // Only the drawing shrinks. The independent 48dp control retains switch semantics and touch size.
    Box(modifier.size(48.dp).then(if (onCheckedChange != null) Modifier.toggleable(
        value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
    ) else Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState(checked) }),
        contentAlignment = Alignment.Center) {
        Switch(checked, null, modifier = Modifier.requiredSize(52.dp, 48.dp)
            .graphicsLayer { scaleX = .82f; scaleY = .82f }.clearAndSetSemantics { },
            thumbContent = thumbContent, enabled = enabled, colors = colors, interactionSource = interactionSource)
    }
}

@Composable
internal fun AdaptiveButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val compact = compactControls()
    Button(onClick, if (compact) compactControlModifier(modifier, 34, 14) else modifier, enabled,
        shape, colors, elevation, border,
        if (compact) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else contentPadding, interactionSource) {
        if (compact) ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)) { content() }
        else content()
    }
}

@Composable
internal fun AdaptiveOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val compact = compactControls()
    OutlinedButton(onClick, if (compact) compactControlModifier(modifier, 34, 14) else modifier, enabled,
        shape, colors, elevation, border,
        if (compact) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else contentPadding, interactionSource) {
        if (compact) ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)) { content() }
        else content()
    }
}

@Composable
internal fun AdaptiveFilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val compact = compactControls()
    FilledTonalButton(onClick, if (compact) compactControlModifier(modifier, 34, 14) else modifier, enabled,
        shape, colors, elevation, border,
        if (compact) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else contentPadding, interactionSource) {
        if (compact) ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)) { content() }
        else content()
    }
}
