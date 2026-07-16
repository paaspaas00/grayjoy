package com.futo.polycentric.core

import android.util.Base64
import com.futo.polycentric.core.serializers.ClaimSerializer
import com.google.protobuf.ByteString
import kotlinx.serialization.Serializable
import userpackage.Protocol
import userpackage.Protocol.Claim
import userpackage.Protocol.URLInfoSystemLink
import java.nio.ByteBuffer

fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun String.hexStringToByteArray(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun ByteArray.toBase64(): String {
    return Base64.encodeToString(this, Base64.NO_PADDING or Base64.NO_WRAP)
}
fun ByteArray.toBase64Url(): String {
    return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
fun String.base64UrlToByteArray(): ByteArray {
    return Base64.decode(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

fun String.base64ToByteArray(): ByteArray {
    return Base64.decode(this, Base64.NO_PADDING or Base64.NO_WRAP)
}

@Serializable
data class OwnedClaim(val system: PublicKey, @Serializable(with = ClaimSerializer::class) val claim: Claim)
fun List<SignedEvent>.getValidClaims(): List<OwnedClaim> {
    val validClaims = arrayListOf<OwnedClaim>()

    for (e in this) {
        if (e.event.contentType == ContentType.CLAIM.value) {
            val claim = getClaimIfValid(e)
            if (claim != null) {
                validClaims.add(claim)
            }
        }
    }

    return validClaims
}

fun List<SignedEvent>.getClaimIfValid(claimEvent: SignedEvent): OwnedClaim? {
    if (claimEvent.event.contentType != ContentType.CLAIM.value) {
        return null
    }

    val claimEventPointer = claimEvent.toPointer()
    for (e in this) {
        if (e.event.contentType != ContentType.VOUCH.value) {
            continue
        }

        val referenceMatches = e.event.references.any { r ->
            val referencePointer = Pointer.fromProto(Protocol.Pointer.parseFrom(r.reference))
            claimEventPointer == referencePointer
        }

        if (referenceMatches) {
            return OwnedClaim(claimEvent.event.system, Claim.parseFrom(claimEvent.event.content))
        }
    }

    return null
}

fun Pointer.toURLInfoEventLinkUrl(servers: Iterable<String>): String {
    return this.toProto().toURLInfoEventLinkUrl(servers)
}

fun Protocol.Pointer.toURLInfoEventLinkUrl(servers: Iterable<String>): String {
    val urlInfo = Protocol.URLInfo.newBuilder()
        .setUrlType(2)
        .setBody(Protocol.URLInfoEventLink.newBuilder()
            .setSystem(system)
            .setProcess(process)
            .setLogicalClock(logicalClock)
            .addAllServers(servers)
            .build()
            .toByteString())
        .build()

    return "polycentric://" + urlInfo.toByteArray().toBase64Url()
}

fun PublicKey.systemToURLInfoSystemLinkUrl(servers: Iterable<String>): String {
    return this.toProto().systemToURLInfoSystemLinkUrl(servers)
}

fun Protocol.PublicKey.systemToURLInfoSystemLinkUrl(servers: Iterable<String>): String {
    val urlInfo = Protocol.URLInfo.newBuilder()
        .setUrlType(1)
        .setBody(URLInfoSystemLink.newBuilder()
            .setSystem(this)
            .addAllServers(servers)
            .build()
            .toByteString())
        .build()

    return "polycentric://" + urlInfo.toByteArray().toBase64Url()
}

fun combineHashCodes(hashCodes: List<Int?>): Int {
    var result = 1
    for (hashCode in hashCodes) {
        result = 31 * result + (hashCode ?: 0)
    }
    return result
}

fun Protocol.ImageManifest.toURLInfoDataLink(system: Protocol.PublicKey, process: Protocol.Process, servers: Iterable<String>): Protocol.URLInfoDataLink {
    return Protocol.URLInfoDataLink.newBuilder()
        .setSystem(system)
        .setProcess(process)
        .addAllServers(servers)
        .setByteCount(byteCount)
        .addAllSections(sectionsList)
        .setMime(mime)
        .build()
}

fun Protocol.ImageManifest.toURLInfoSystemLinkUrl(processHandle: ProcessHandle, servers: Iterable<String>): String {
    return toURLInfoSystemLinkUrl(processHandle.system.toProto(), processHandle.processSecret.process.toProto(), servers)
}

fun Protocol.ImageManifest.toURLInfoSystemLinkUrl(system: Protocol.PublicKey, process: Protocol.Process, servers: Iterable<String>): String {
    val urlInfo = Protocol.URLInfo.newBuilder()
        .setUrlType(4)
        .setBody(toURLInfoDataLink(system, process, servers).toByteString())
        .build()

    return "polycentric://" + urlInfo.toByteArray().toBase64Url()
}

fun List<SignedEvent>.reassembleSections(totalSize: Int, sections: Iterable<Protocol.Range>): ByteBuffer? {
    val totalArray = ByteBuffer.allocate(totalSize)
    var offset = 0

    for (section in sections) {
        val sectionEvents = filter { it.event.logicalClock >= section.low && it.event.logicalClock <= section.high }.sortedBy { it.event.logicalClock }

        if (sectionEvents.size.toLong() != section.high - section.low + 1L) {
            return null
        }

        sectionEvents.forEach {
            val blobSection = it.event.content
            val size = blobSection.size
            blobSection.copyInto(totalArray.array(), offset)
            offset += size
        }
    }

    return totalArray
}