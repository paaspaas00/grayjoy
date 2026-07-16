package com.futo.platformplayer.models

import android.graphics.Bitmap
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient

@kotlinx.serialization.Serializable
data class ImageVariable(
    val url: String? = null,
    val resId: Int? = null,
    @Transient @Contextual private val bitmap: Bitmap? = null,
    val presetName: String? = null,
    var subscriptionUrl: String? = null,
) {
    companion object {
        fun fromUrl(url: String) = ImageVariable(url = url)
        fun fromResource(id: Int) = ImageVariable(resId = id)
        fun fromBitmap(bitmap: Bitmap) = ImageVariable(bitmap = bitmap)
        fun fromPresetName(name: String) = ImageVariable(presetName = name)
    }
}
