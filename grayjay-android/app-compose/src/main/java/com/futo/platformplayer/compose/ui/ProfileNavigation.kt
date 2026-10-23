package com.futo.platformplayer.compose.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.helpAnchor
import com.futo.platformplayer.compose.ui.screens.SourceIconImage
import kotlin.math.abs

internal data class ProfileNavigation(
    val profiles: List<ProfileUiModel> = emptyList(), val current: String = "",
    val enabled: Boolean = true, val switch: (String) -> Unit = {},
)
internal val LocalProfileNavigation = staticCompositionLocalOf { ProfileNavigation() }

@Composable
internal fun ProfileSourceIcon(profile: ProfileUiModel, modifier: Modifier) {
    if (profile.sourceId !in setOf("youtube", "spotify")) {
        SourceIconImage(profile.name, profile.sourceIconUrl, Color(profile.sourceAccentColor), modifier)
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (profile.sourceId == "youtube") {
            drawRoundRect(Color(0xFFFF0000), Offset(0f, h * .15f), Size(w, h * .7f), CornerRadius(w * .18f))
            drawPath(Path().apply { moveTo(w * .4f, h * .32f); lineTo(w * .7f, h * .5f); lineTo(w * .4f, h * .68f); close() }, Color.White)
        } else {
            drawCircle(Color(0xFF1DB954))
            repeat(3) { row ->
                val y = h * (.35f + .16f * row)
                drawPath(Path().apply {
                    moveTo(w * (.22f + row * .02f), y)
                    quadraticBezierTo(w * .53f, y - h * .12f, w * (.8f - row * .03f), y + h * .03f)
                }, Color(0xFF111111), style = Stroke(w * (.075f - row * .01f), cap = StrokeCap.Round))
            }
        }
    }
}

internal fun nextUnprotectedProfile(profiles: List<ProfileUiModel>, current: String, direction: Int): String? {
    val available = profiles.filter { it.protection == ProfileProtection.None }
    if (available.isEmpty()) return null
    val index = available.indexOfFirst { it.id == current }
    val target = if (index < 0) {
        if (direction > 0) available.first() else available.last()
    } else available[Math.floorMod(index + if (direction > 0) 1 else -1, available.size)]
    return target.id.takeUnless { it == current }
}

@Composable
internal fun ProfileMenuButton(onOpen: () -> Unit) {
    val navigation = LocalProfileNavigation.current
    val current = navigation.profiles.firstOrNull { it.id == navigation.current }
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    val latest by rememberUpdatedState(navigation)
    val label = stringResource(R.string.open_profile)
    IconButton(onClick = onOpen, enabled = navigation.enabled,
        modifier = Modifier.testTag("profile-menu").helpAnchor("profile-menu").semantics { contentDescription = label }
            .pointerInput(navigation.enabled, navigation.profiles, navigation.current) {
                if (!navigation.enabled) return@pointerInput
                var moved = 0f
                detectHorizontalDragGestures(onDragStart = { moved = 0f }, onDragCancel = { moved = 0f },
                    onDragEnd = {
                        if (abs(moved) >= threshold) nextUnprotectedProfile(latest.profiles, latest.current,
                            if (moved < 0f) 1 else -1)?.let(latest.switch)
                        moved = 0f
                    }) { change, distance -> change.consume(); moved += distance }
            }) {
        if (current?.sourceId?.isNotBlank() == true) ProfileSourceIcon(current, Modifier.size(28.dp))
        else Icon(Icons.Outlined.AccountCircle, stringResource(R.string.open_profile))
    }
}
