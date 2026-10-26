package com.futo.platformplayer.compose.ui.screens

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.resume

private data class SeekBlurFrame(val bitmap: Bitmap, val image: ImageBitmap, val bounds: Rect)

/** Only the video layer is blurred. The timeline, previews and buttons stay sharp. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SeekVideoBlur(playerView: PlayerView?, seeking: Boolean, protectedContent: Boolean) {
    val amount = animateFloatAsState(if (seeking) 1f else 0f, tween(160), label = "seek-video-blur")
    val seekingNow by rememberUpdatedState(seeking)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surface = playerView?.videoSurfaceView
    val nativeTexture = surface is TextureView && Build.VERSION.SDK_INT >= 31 && !protectedContent
    val radiusPx = with(LocalDensity.current) { 6.dp.toPx() }
    val active = seeking || amount.value > .001f

    LaunchedEffect(surface, nativeTexture, radiusPx, lifecycle) {
        if (nativeTexture && Build.VERSION.SDK_INT >= 31) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var currentGesture = seekingNow
                try {
                    snapshotFlow { (amount.value * radiusPx * 2).roundToInt() / 2f }.collect { radius ->
                        if (seekingNow) currentGesture = true
                        setTextureBlur(surface, if (currentGesture) radius else 0f)
                    }
                } finally { setTextureBlur(surface, 0f) }
            }
        }
    }
    DisposableEffect(surface, nativeTexture) {
        onDispose { if (nativeTexture && Build.VERSION.SDK_INT >= 31) setTextureBlur(surface, 0f) }
    }

    val frame by produceState<SeekBlurFrame?>(null, playerView, surface, active, protectedContent, nativeTexture, lifecycle) {
        value = null
        if (!active || protectedContent || nativeTexture || playerView == null || surface == null) {
            value = null
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!seekingNow) return@repeatOnLifecycle
            var failures = 0
            try {
                while (isActive) {
                    val next = captureSeekFrame(playerView, surface)
                    if (next != null) {
                        if (Build.VERSION.SDK_INT < 31) withContext(Dispatchers.Default) {
                            val pixels = IntArray(next.bitmap.width * next.bitmap.height)
                            next.bitmap.getPixels(pixels, 0, next.bitmap.width, 0, 0, next.bitmap.width, next.bitmap.height)
                            blurSeekPixels(pixels, next.bitmap.width, next.bitmap.height, 4)
                            next.bitmap.setPixels(pixels, 0, next.bitmap.width, 0, 0, next.bitmap.width, next.bitmap.height)
                        }
                        value = next
                        failures = 0
                    } else if (++failures >= 3) break
                    // Keep at most one asynchronous copy in flight, only during this gesture.
                    delay(80)
                }
            } finally { value = null }
        }
    }
    if (!active) return
    Box(Modifier.fillMaxSize().testTag("player-seek-blur"))
    if (protectedContent) {
        // Secure decoder buffers must never enter the UI renderer or a capture path.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = amount.value * .12f }.background(Color.Black))
        return
    }
    val snapshot = frame ?: return
    val density = LocalDensity.current
    Canvas(Modifier.offset { IntOffset(snapshot.bounds.left.roundToInt(), snapshot.bounds.top.roundToInt()) }
        .size(with(density) { snapshot.bounds.width.toDp() }, with(density) { snapshot.bounds.height.toDp() })
        .testTag("player-seek-blur-frame")
        .graphicsLayer {
            clip = true
            alpha = amount.value
            val radius = amount.value * radiusPx
            renderEffect = if (Build.VERSION.SDK_INT >= 31 && radius > .05f)
                BlurEffect(radius, radius, TileMode.Clamp) else null
        }) {
        drawImage(snapshot.image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

@RequiresApi(31)
private fun setTextureBlur(view: View, radius: Float) {
    view.setRenderEffect(if (radius > .05f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
}

private suspend fun captureSeekFrame(parent: PlayerView, surface: View): SeekBlurFrame? {
    if (!surface.isAttachedToWindow || surface.width <= 0 || surface.height <= 0) return null
    if (surface is SurfaceView && !surface.holder.surface.isValid) return null
    if (surface is TextureView && !surface.isAvailable) return null
    val scale = minOf(1f, 320f / max(surface.width, surface.height))
    val width = (surface.width * scale).roundToInt().coerceAtLeast(1)
    val height = (surface.height * scale).roundToInt().coerceAtLeast(1)
    var x = 0f
    var y = 0f
    var node: View? = surface
    while (node != null && node !== parent) {
        x += node.x; y += node.y
        node = node.parent as? View
    }
    if (node == null) return null
    val bounds = Rect(x, y, x + surface.width, y + surface.height)
    val bitmap = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
        catch (_: OutOfMemoryError) { return null }
    return suspendCancellableCoroutine { continuation ->
        var completed = false
        fun complete(success: Boolean) {
            if (completed) return
            completed = true
            if (success && continuation.isActive) continuation.resume(SeekBlurFrame(bitmap, bitmap.asImageBitmap(), bounds))
            else {
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(null)
            }
        }
        try {
            when (surface) {
                is SurfaceView -> PixelCopy.request(surface, bitmap, { result -> complete(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
                is TextureView -> { surface.getBitmap(bitmap); complete(true) }
                else -> complete(false)
            }
        } catch (_: IllegalArgumentException) { complete(false) }
        catch (_: IllegalStateException) { complete(false) }
        catch (_: SecurityException) { complete(false) }
    }
}

/** Small software fallback for Android 8–11; never runs on the UI thread. */
internal fun blurSeekPixels(pixels: IntArray, width: Int, height: Int, radius: Int) {
    require(width > 0 && height > 0 && pixels.size == width * height && radius in 1..16)
    val scratch = IntArray(pixels.size)
    val count = radius * 2 + 1
    repeat(2) {
        for (vertical in listOf(false, true)) {
            val source = if (vertical) scratch else pixels
            val target = if (vertical) pixels else scratch
            val lines = if (vertical) width else height
            val length = if (vertical) height else width
            for (line in 0 until lines) {
                fun index(position: Int) = if (vertical) position.coerceIn(0, length - 1) * width + line
                    else line * width + position.coerceIn(0, length - 1)
                var a = 0; var r = 0; var g = 0; var b = 0
                for (i in -radius..radius) {
                    val color = source[index(i)]
                    a += color ushr 24; r += (color ushr 16) and 255; g += (color ushr 8) and 255; b += color and 255
                }
                for (position in 0 until length) {
                    target[index(position)] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                    val old = source[index(position - radius)]
                    val next = source[index(position + radius + 1)]
                    a += (next ushr 24) - (old ushr 24)
                    r += ((next ushr 16) and 255) - ((old ushr 16) and 255)
                    g += ((next ushr 8) and 255) - ((old ushr 8) and 255)
                    b += (next and 255) - (old and 255)
                }
            }
        }
    }
}
