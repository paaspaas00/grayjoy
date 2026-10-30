package com.futo.platformplayer.compose.ui.screens

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** One UI target per player across Now Playing, fullscreen and PiP. Main-thread only. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object PlayerViewTargets {
    private val targets = WeakHashMap<Player, WeakReference<PlayerView>>()

    fun bounds(player: Player): Rect? = targets[player]?.get()?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }?.let {
        val location = IntArray(2); it.getLocationInWindow(location)
        Rect(location[0].toFloat(), location[1].toFloat(), (location[0] + it.width).toFloat(), (location[1] + it.height).toFloat())
    }

    /** One bounded, in-memory transition frame. Protected surfaces are never copied. */
    suspend fun transitionFrame(player: Player, drmProtected: Boolean, maximumSide: Int = 640): Bitmap? {
        if (drmProtected) return null
        val view = targets[player]?.get()?.takeIf { it.isAttachedToWindow && it.alpha > .95f } ?: return null
        val surface = view.videoSurfaceView ?: return null
        if (surface.width <= 0 || surface.height <= 0 || !surface.isAttachedToWindow) return null
        val scale = minOf(1f, maximumSide.coerceIn(1, 640).toFloat() / maxOf(surface.width, surface.height))
        val width = (surface.width * scale).roundToInt().coerceAtLeast(1)
        val height = (surface.height * scale).roundToInt().coerceAtLeast(1)
        if (surface is TextureView) return runCatching { surface.getBitmap(width, height) }.getOrNull()
        if (surface !is SurfaceView || android.os.Build.VERSION.SDK_INT < 24) return null
        return withTimeoutOrNull(80) { suspendCancellableCoroutine { continuation ->
            val bitmap = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
                catch (_: OutOfMemoryError) { continuation.resume(null); return@suspendCancellableCoroutine }
            try {
                PixelCopy.request(surface, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS && continuation.isActive) continuation.resume(bitmap)
                    else { bitmap.recycle(); if (continuation.isActive) continuation.resume(null) }
                }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) {
                bitmap.recycle(); if (continuation.isActive) continuation.resume(null)
            }
        } }
    }

    fun attach(view: PlayerView, player: Player) {
        if (view.player != null && view.player !== player) detach(view)
        val previous = targets[player]?.get()
        if (previous === view && view.player === player) return
        // Media3 attaches the new surface before releasing the old target. An old PiP
        // view must not retain the player and later reclaim its video output.
        PlayerView.switchTargetView(player, previous?.takeUnless { it === view }, view)
        targets[player] = WeakReference(view)
    }

    fun detach(view: PlayerView) {
        val player = view.player
        if (player != null && targets[player]?.get() === view) targets.remove(player)
        view.player = null
    }
}
