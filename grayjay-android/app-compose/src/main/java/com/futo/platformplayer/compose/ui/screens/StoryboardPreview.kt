package com.futo.platformplayer.compose.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.compose.ui.StoryboardUiModel

internal data class StoryboardFrameUiModel(
    val sheetUrl: String,
    val cellWidth: Int,
    val cellHeight: Int,
    val column: Int,
    val row: Int,
    val frameIndex: Int,
)

internal fun StoryboardUiModel.frameAt(
    positionMs: Long,
    targetWidthPx: Int,
): StoryboardFrameUiModel? {
    val usableLevels = levels.filter { level ->
        level.width > 0 && level.height > 0 && level.frameCount > 0 &&
            level.columns > 0 && level.rows > 0 && level.intervalMs > 0L &&
            level.sheetUrlTemplate.isNotBlank()
    }
    val target = targetWidthPx.coerceAtLeast(1)
    val level = usableLevels.filter { it.width >= target }.minByOrNull { it.width }
        ?: usableLevels.maxByOrNull { it.width }
        ?: return null
    val frameIndex = (positionMs.coerceAtLeast(0L) / level.intervalMs)
        .coerceAtMost((level.frameCount - 1).toLong())
        .toInt()
    val sheetCapacity = level.columns * level.rows
    val sheetIndex = frameIndex / sheetCapacity
    val cellIndex = frameIndex % sheetCapacity
    val sheetUrl = level.sheetUrlTemplate.replace("\$M", sheetIndex.toString())
    if (sheetUrl.contains("\$M")) return null
    return StoryboardFrameUiModel(
        sheetUrl = sheetUrl,
        cellWidth = level.width,
        cellHeight = level.height,
        column = cellIndex % level.columns,
        row = cellIndex / level.columns,
        frameIndex = frameIndex,
    )
}

/** Draws one cell from a Glide-cached YouTube sprite without creating per-frame bitmaps. */
internal class StoryboardFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var onLoadFailure: (() -> Unit)? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var frame: StoryboardFrameUiModel? = null
    private var requestedUrl: String? = null
    private var requestTarget: CustomTarget<Bitmap>? = null

    fun showFrame(nextFrame: StoryboardFrameUiModel) {
        frame = nextFrame
        invalidate()
        if (requestedUrl == nextFrame.sheetUrl) return

        requestTarget?.let { Glide.with(context).clear(it) }
        requestTarget = null
        requestedUrl = nextFrame.sheetUrl
        bitmap = null

        val targetUrl = nextFrame.sheetUrl
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(
                resource: Bitmap,
                transition: Transition<in Bitmap>?,
            ) {
                if (requestedUrl != targetUrl) return
                bitmap = resource
                invalidate()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (requestedUrl != targetUrl) return
                bitmap = null
                onLoadFailure?.invoke()
                invalidate()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (requestedUrl == targetUrl) {
                    bitmap = null
                    invalidate()
                }
            }
        }
        requestTarget = target
        Glide.with(context)
            .asBitmap()
            .load(targetUrl)
            // DATA keeps the sprite itself on disk; moving to another cell on the same
            // sheet only changes the source rectangle and performs no network request.
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .dontAnimate()
            .into(target)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val sourceBitmap = bitmap ?: return
        val currentFrame = frame ?: return
        val left = currentFrame.column * currentFrame.cellWidth
        val top = currentFrame.row * currentFrame.cellHeight
        val right = left + currentFrame.cellWidth
        val bottom = top + currentFrame.cellHeight
        if (left < 0 || top < 0 || right > sourceBitmap.width || bottom > sourceBitmap.height) {
            return
        }

        val source = Rect(left, top, right, bottom)
        val scale = minOf(
            width.toFloat() / currentFrame.cellWidth.toFloat(),
            height.toFloat() / currentFrame.cellHeight.toFloat(),
        )
        val renderedWidth = currentFrame.cellWidth * scale
        val renderedHeight = currentFrame.cellHeight * scale
        val destination = RectF(
            (width - renderedWidth) / 2f,
            (height - renderedHeight) / 2f,
            (width + renderedWidth) / 2f,
            (height + renderedHeight) / 2f,
        )
        canvas.drawBitmap(sourceBitmap, source, destination, bitmapPaint)
    }

    override fun onDetachedFromWindow() {
        requestTarget?.let { Glide.with(context).clear(it) }
        requestTarget = null
        requestedUrl = null
        bitmap = null
        super.onDetachedFromWindow()
    }
}
