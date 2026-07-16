package com.futo.polycentric.core.serializers

import com.futo.polycentric.core.base64UrlToByteArray
import com.futo.polycentric.core.toBase64Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import userpackage.Protocol

class ImageBundleSerializer : KSerializer<Protocol.ImageBundle> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ImageBundle", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Protocol.ImageBundle) {
        encoder.encodeString(value.toByteArray().toBase64Url())
    }
    override fun deserialize(decoder: Decoder): Protocol.ImageBundle {
        val value = decoder.decodeString()
        return Protocol.ImageBundle.parseFrom(value.base64UrlToByteArray())
    }
}