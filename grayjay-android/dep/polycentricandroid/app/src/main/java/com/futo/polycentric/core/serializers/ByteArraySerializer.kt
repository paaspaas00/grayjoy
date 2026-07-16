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

object ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.futo.polycentric.core.serializers.ByteArray", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(value.toBase64Url())
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val string = decoder.decodeString()
        return string.base64UrlToByteArray()
    }
}