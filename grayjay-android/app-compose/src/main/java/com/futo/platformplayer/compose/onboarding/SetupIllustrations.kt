package com.futo.platformplayer.compose.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

internal enum class SetupDemo { Welcome, Shorts, Hold, Sponsor }

/** Resolution-independent vector artwork: no WebView, bitmap frames or network assets. */
@Composable
internal fun WizardMark(modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_wizard), contentDescription = null,
        modifier = modifier, tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SetupIllustration(demo: SetupDemo, active: Boolean = true) {
    if (demo == SetupDemo.Shorts) {
        ShortsGestureDemo(active)
        return
    }
    val phase = if (active && demo != SetupDemo.Welcome) {
        val animation = rememberInfiniteTransition(label = "setup-demo")
        animation.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gesture").value
    } else 0f
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = stringResource(when (demo) {
        SetupDemo.Welcome -> R.string.setup_title
        SetupDemo.Shorts -> R.string.setup_shorts_animation
        SetupDemo.Hold -> R.string.setup_hold_animation
        SetupDemo.Sponsor -> R.string.setup_sponsor_animation
    })
    Box(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (demo == SetupDemo.Welcome) WizardMark(Modifier.size(112.dp))
        else Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (demo == SetupDemo.Hold) {
                drawRoundRect(background, Offset(w * .12f, h * .12f), Size(w * .76f, h * .76f), CornerRadius(18.dp.toPx()))
                drawLine(primary, Offset(w * .18f, h * .77f), Offset(w * (.18f + phase * .62f), h * .77f), 5.dp.toPx())
                drawCircle(primary.copy(alpha = .15f), (18 + 9 * phase).dp.toPx(), Offset(w * .75f, h * .5f))
                drawCircle(primary, 12.dp.toPx(), Offset(w * .75f, h * .5f))
            } else {
                val y = h * .5f
                drawLine(background, Offset(w * .08f, y), Offset(w * .92f, y), 12.dp.toPx())
                drawLine(Color(0xFFA5D6A7), Offset(w * .4f, y), Offset(w * .6f, y), 12.dp.toPx())
                val progress = if (phase < .5f) .08f + phase * .64f else .6f + (phase - .5f) * .64f
                drawCircle(primary, 9.dp.toPx(), Offset(w * progress, y))
                drawArc(primary, 190f, 160f, false, Offset(w * .39f, y - 34.dp.toPx()),
                    Size(w * .22f, 48.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        if (demo == SetupDemo.Hold) Text(if (phase > .25f) "2×" else "1×", style = MaterialTheme.typography.headlineLarge)
    }
}
