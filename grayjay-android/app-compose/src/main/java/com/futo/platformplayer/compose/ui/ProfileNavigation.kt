package com.futo.platformplayer.compose.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.onboarding.helpAnchor
import com.futo.platformplayer.compose.ui.screens.SourceIconImage
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val latest by rememberUpdatedState(navigation)
    val density = LocalDensity.current
    val movement = with(density) { 28.dp.toPx() } * .4f
    val threshold = with(density) { 8.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }
    var settling by remember { mutableStateOf(false) }
    var originId by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ProfileUiModel?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pendingId by remember { mutableStateOf<String?>(null) }
    var observedBusy by remember { mutableStateOf(false) }

    fun resetVisuals() {
        settling = false; originId = null; preview = null
        offset = 0f; opacity = 1f; scale = 1f
        pendingId = null; observedBusy = false
    }

    // An acknowledged model change must not interrupt the incoming icon animation.
    LaunchedEffect(navigation.current, navigation.enabled, settling) {
        when {
            originId != null && navigation.current != originId && navigation.current != pendingId -> {
                animation?.cancel(); resetVisuals()
            }
            pendingId != null && navigation.current == pendingId && !settling -> resetVisuals()
            pendingId != null && !navigation.enabled -> observedBusy = true
            pendingId != null && observedBusy && navigation.enabled && !settling -> resetVisuals()
        }
    }

    fun switchWithMotion(direction: Int) {
        if (!latest.enabled || settling || pendingId != null) return
        originId = latest.current
        preview = latest.profiles.firstOrNull { it.id == latest.current }
        val targetId = nextUnprotectedProfile(latest.profiles, latest.current, direction)
        val target = latest.profiles.firstOrNull { it.id == targetId }
        settling = true
        animation = scope.launch {
            if (target == null) {
                // A single available profile gives a small nudge, never a false switch.
                animate(0f, -direction * movement, animationSpec = tween(100)) { value, _ -> offset = value }
                animate(offset, 0f, animationSpec = tween(100)) { value, _ -> offset = value }
                resetVisuals()
                return@launch
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            animate(0f, 1f, animationSpec = tween(100)) { progress, _ ->
                offset = -direction * movement * progress
                opacity = 1f - ((progress - .5f) * 2f).coerceIn(0f, 1f)
            }
            if (!latest.enabled || latest.current != originId ||
                latest.profiles.none { it.id == target.id && it.protection == ProfileProtection.None }) {
                resetVisuals()
                return@launch
            }
            pendingId = target.id
            preview = target
            offset = 0f; opacity = .125f; scale = .067f
            animate(0f, 1f, animationSpec = tween(233, easing = FastOutSlowInEasing)) { progress, _ ->
                scale = .067f + .933f * progress
                opacity = .125f + .875f * (progress * 2f).coerceAtMost(1f)
            }
            // Switching can replace this header (for example Search -> Home). Finish
            // the visual transition before publishing the new profile to the app.
            if (!latest.enabled || latest.current != originId ||
                latest.profiles.none { it.id == target.id && it.protection == ProfileProtection.None }) {
                resetVisuals()
                return@launch
            }
            latest.switch(target.id)
            settling = false
            if (latest.current == target.id) resetVisuals()
            else {
                // A rejected/no-op switch must not leave the button permanently locked.
                delay(500)
                if (pendingId == target.id && latest.enabled && latest.current == originId) resetVisuals()
            }
        }
    }

    val label = stringResource(R.string.open_profile)
    IconButton(onClick = onOpen, enabled = navigation.enabled && !settling && pendingId == null,
        modifier = Modifier.testTag("profile-menu").helpAnchor("profile-menu").semantics {
            contentDescription = label
            current?.let { stateDescription = it.name }
        }.pointerInput(density) {
            var distance = Offset.Zero
            var handled = false
            // One detector owns the gesture. It never re-arms after a reversal or an
            // asynchronous profile update while the finger is still on the screen.
            detectDragGestures(
                onDragStart = { distance = Offset.Zero; handled = !latest.enabled || settling || pendingId != null },
                onDragCancel = {},
                onDragEnd = {},
            ) { change, delta ->
                change.consume()
                if (!handled) {
                    distance += delta
                    if (abs(distance.x) >= threshold || abs(distance.y) >= threshold) {
                        handled = true
                        val direction = profileSwipeDirection(distance.x, distance.y, threshold)
                        if (direction != 0) switchWithMotion(direction)
                    }
                }
            }
        }) {
        Box(Modifier.size(40.dp).clipToBounds(), contentAlignment = Alignment.Center) {
            ProfileMenuAvatar(if (originId != null) preview else current,
                Modifier.graphicsLayer {
                    translationY = offset
                    alpha = opacity
                    scaleX = scale
                    scaleY = scale
                }.testTag("profile-avatar-current"))
        }
    }
}

@Composable
private fun ProfileMenuAvatar(profile: ProfileUiModel?, modifier: Modifier) {
    Box(modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (profile?.sourceId?.isNotBlank() == true) ProfileSourceIcon(profile,
            Modifier.size(28.dp).clearAndSetSemantics { })
        else Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun profileSwipeDirection(dx: Float, dy: Float, threshold: Float): Int {
    if (!dx.isFinite() || !dy.isFinite() || !threshold.isFinite() || threshold <= 0f) return 0
    if (abs(dy) < threshold || abs(dy) <= abs(dx)) return 0
    return if (dy < 0f) 1 else -1
}
