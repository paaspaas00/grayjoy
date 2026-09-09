package com.futo.platformplayer.compose.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.futo.platformplayer.compose.images.ArtworkCache
import com.futo.platformplayer.compose.images.ArtworkKind
import com.futo.platformplayer.compose.images.CachedArtwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/** Thumbnail completion invalidates drawing only; there is no nested Android View to lay out. */
@Composable
internal fun RemoteBitmapImage(
    url: String,
    placeholderColor: Color,
    modifier: Modifier,
    fallbackUrl: String? = null,
    requestHeaders: Map<String, String> = emptyMap(),
    circleCrop: Boolean = false,
    requestSize: IntSize? = null,
    artworkKind: ArtworkKind = if (circleCrop) ArtworkKind.Avatar else ArtworkKind.Thumbnail,
    showParentPlaceholderWhileLoading: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { ArtworkCache.get(context) }
    val generation by cache.generation.collectAsStateWithLifecycle()
    var resumeGeneration by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { resumeGeneration++ }
    val web = url.startsWith("https://") || url.startsWith("http://")
    val cachedFlow = remember(url, requestHeaders, artworkKind, generation, resumeGeneration) {
        if (web) cache.observe(url, requestHeaders, artworkKind) else MutableStateFlow<CachedArtwork?>(null)
    }
    val cached by cachedFlow.collectAsStateWithLifecycle()
    val fallbackFlow = remember(fallbackUrl, requestHeaders, artworkKind, generation, resumeGeneration, cached != null && cached?.file == null) {
        if (web && cached != null && cached?.file == null && !fallbackUrl.isNullOrBlank()) cache.observe(fallbackUrl, requestHeaders, artworkKind)
        else MutableStateFlow<CachedArtwork?>(null)
    }
    val cachedFallback by fallbackFlow.collectAsStateWithLifecycle()
    val artwork = cached?.takeIf { it.file != null } ?: cachedFallback
    val model: Any? = if (web) artwork?.file else url
    val measuredSize = remember { mutableStateOf(IntSize.Zero) }
    val targetSize = requestSize ?: measuredSize.value
    val bitmap = remember(url, fallbackUrl, requestHeaders, circleCrop, targetSize, generation) {
        mutableStateOf<ImageBitmap?>(null)
    }
    DisposableEffect(context, model, artwork?.revision, requestHeaders, circleCrop, targetSize, generation) {
        var active = true
        val manager = Glide.with(context)
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (active) bitmap.value = resource.asImageBitmap()
            }
            override fun onLoadCleared(placeholder: Drawable?) { bitmap.value = null }
            override fun onLoadFailed(errorDrawable: Drawable?) { bitmap.value = null }
        }
        if (model != null && url.isNotBlank() && targetSize.width > 0 && targetSize.height > 0) {
            fun request(address: String) = manager.asBitmap()
                .load(if (requestHeaders.isEmpty()) address else GlideUrl(address, LazyHeaders.Builder().apply {
                    requestHeaders.forEach { (name, value) -> addHeader(name, value) }
                }.build()))
                .override(targetSize.width, targetSize.height)
                .dontAnimate()
                .let { if (circleCrop) it.circleCrop() else it.centerCrop() }
            val primary = if (web) manager.asBitmap().load(model).signature(ObjectKey(artwork?.revision ?: 0L))
                .diskCacheStrategy(DiskCacheStrategy.NONE).override(targetSize.width, targetSize.height).dontAnimate()
                .let { if (circleCrop) it.circleCrop() else it.centerCrop() }
                else request(url)
            (if (!web && fallbackUrl != null && fallbackUrl != url) primary.error(request(fallbackUrl)) else primary).into(target)
        }
        onDispose {
            active = false
            bitmap.value = null
            manager.clear(target)
        }
    }
    Canvas(modifier.then(if (requestSize == null) Modifier.onSizeChanged { size ->
        val scale = (384f / maxOf(size.width, size.height).coerceAtLeast(1)).coerceAtMost(1f)
        measuredSize.value = IntSize((size.width * scale).roundToInt(), (size.height * scale).roundToInt())
    } else Modifier)) {
        if (bitmap.value != null || !showParentPlaceholderWhileLoading) drawRect(placeholderColor)
        bitmap.value?.let { image ->
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
