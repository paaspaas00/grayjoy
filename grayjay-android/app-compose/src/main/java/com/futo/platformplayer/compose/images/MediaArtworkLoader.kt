package com.futo.platformplayer.compose.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Supplies actual, bounded-size cover bitmaps to Media3's platform/AVRCP metadata bridge. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class MediaArtworkLoader(
    context: Context,
    private val headersFor: (String) -> Map<String, String> = { emptyMap() },
) : BitmapLoader {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun supportsMimeType(mimeType: String) = mimeType.lowercase() in setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "image/heif", "image/heic", "image/avif",
    )
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = submit { decode(data) }
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val headers = headersFor(uri.toString())
        return submit {
            val bytes = if (uri.scheme == "http" || uri.scheme == "https") {
                val artwork = withTimeout(25_000) { ArtworkCache.get(app).observe(uri.toString(), headers).filterNotNull().first() }
                (artwork.file ?: throw IOException("Cover artwork unavailable")).readBytes()
            } else if (uri.toString().startsWith("file:///android_asset/")) {
                app.assets.open(uri.toString().removePrefix("file:///android_asset/")).use { it.readBytes() }
            } else app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Cover artwork unavailable")
            decode(bytes)
        }
    }
    private fun submit(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val result = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try { result.set(block()) }
            catch (cancelled: CancellationException) { result.cancel(false); throw cancelled }
            catch (error: Exception) { result.setException(error) }
        }
        result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return result
    }
    private fun decode(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Cannot decode cover artwork")
        val ratio = (256f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        return if (ratio == 1f) bitmap else Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true).also { bitmap.recycle() }
    }
    fun close() { scope.cancel() }
}
