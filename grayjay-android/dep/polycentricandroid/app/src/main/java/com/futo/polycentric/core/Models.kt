package com.futo.polycentric.core

import com.futo.polycentric.core.serializers.ByteArraySerializer
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.serialization.Serializable
import userpackage.Protocol
import java.security.MessageDigest

enum class ContentType(val value: Long) {
    DELETE(1),
    SYSTEM_PROCESSES(2),
    POST(3),
    FOLLOW(4),
    USERNAME(5),
    DESCRIPTION(6),
    BLOB_SECTION(8),
    AVATAR(9),
    SERVER(10),
    VOUCH(11),
    CLAIM(12),
    BANNER(13),
    OPINION(14),
    STORE(15),
    AUTHORITY(16),
    STORE_DATA(17),
    PROMOTION_BANNER(18),
    PROMOTION(19),
    MEMBERSHIP_URLS(20),
    DONATION_DESTINATIONS(21)
}

enum class ClaimType(val value: Long) {
    HACKER_NEWS(1),
    YOUTUBE(2),
    ODYSEE(3),
    RUMBLE(4),
    TWITTER(5),
    BITCOIN(6),
    GENERIC(7),
    DISCORD(8),
    INSTAGRAM(9),
    GITHUB(10),
    MINDS(11),
    PATREON(12),
    SUBSTACK(13),
    TWITCH(14),
    WEBSITE(15),
    KICK(16),
    SOUNDCLOUD(17),
    VIMEO(18),
    NEBULA(19),
    URL(20),
    OCCUPATION(21),
    SKILL(22),
    SPOTIFY(23),
    SPREADSHOP(24),
    POLYCENTRIC(25)
}

class Blob(val mime: String, val content: ByteArray) { }
@Serializable
class Digest(val digestType: Long, @Serializable(with = ByteArraySerializer::class) val digest: ByteArray) {
    init {
        if (digestType != 1L) {
            throw IllegalArgumentException("unknown digest type")
        }

        if (digest.size != 32) {
            throw IllegalArgumentException("incorrect digest length")
        }
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(digestType.hashCode(), digest.contentHashCode()))
    }

    override fun toString(): String {
        return "(digestType: ${digestType}, digest: ${digest.toHexString()})"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Digest) {
            return false
        }

        return digestType == other.digestType && digest.contentEquals(other.digest)
    }

    fun toProto(): Protocol.Digest {
        return Protocol.Digest.newBuilder()
            .setDigestType(digestType)
            .setDigest(ByteString.copyFrom(digest))
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.Digest): Digest {
            return Digest(proto.digestType, proto.digest.toByteArray())
        }
    }
}

fun ByteArray.hash(): Digest {
    val context = MessageDigest.getInstance("SHA-256")
    val digest = context.digest(this)
    return Digest(1L, digest)
}

@Serializable
data class Pointer(
    val system: PublicKey,
    val process: Process,
    val logicalClock: Long,
    val digest: Digest
) {
    fun toProto(): Protocol.Pointer {
        return Protocol.Pointer.newBuilder()
            .setSystem(system.toProto())
            .setProcess(process.toProto())
            .setLogicalClock(logicalClock)
            .setEventDigest(digest.toProto())
            .build()
    }

    fun toReference(): Protocol.Reference {
        return Protocol.Reference.newBuilder()
            .setReferenceType(2)
            .setReference(ByteString.copyFrom(toProto().toByteArray()))
            .build()
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(system.hashCode(), process.hashCode(), logicalClock.hashCode(), digest.hashCode()))
    }

    override fun toString(): String {
        return super.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Pointer) {
            return false
        }

        return system == other.system && process == other.process && logicalClock == other.logicalClock && digest == other.digest
    }

    companion object {
        fun fromProto(proto: Protocol.Pointer): Pointer {
            if (!proto.hasSystem()) {
                throw Error("expected system")
            }

            if (!proto.hasProcess()) {
                throw Error("expected process")
            }

            if (!proto.hasEventDigest()) {
                throw Error("expected digest")
            }

            return Pointer(
                PublicKey.fromProto(proto.system),
                Process.fromProto(proto.process),
                proto.logicalClock,
                Digest.fromProto(proto.eventDigest),
            )
        }

        fun fromSignedEvent(signedEvent: SignedEvent): Pointer {
            val event = signedEvent.event
            return Pointer(
                event.system,
                event.process,
                event.logicalClock,
                signedEvent.rawEvent.hash()
            )
        }
    }
}

data class ImageData(val mimeType: String, val width: Int, val height: Int, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (other is ImageData) {
            return mimeType == other.mimeType && width == other.width && height == other.height && data.contentEquals(other.data)
        }

        return false
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(width, height, mimeType.hashCode(), data.contentHashCode()))
    }
}

data class Opinion(val data: ByteArray) {
    companion object {
        fun makeOpinion(x: Int): Opinion {
            return Opinion(ByteArray(1) { x.toByte() })
        }

        val like: Opinion get() = makeOpinion(1)
        val dislike: Opinion get() = makeOpinion(2)
        val neutral: Opinion get() = makeOpinion(3)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Opinion) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

@Serializable
data class PublicKey(val keyType: Long, @Serializable(with = ByteArraySerializer::class) val key: ByteArray) {
    init {
        if (keyType != 1L) {
            throw IllegalArgumentException("unknown key type")
        }

        if (key.size != 32) {
            throw IllegalArgumentException("incorrect public key length")
        }
    }

    fun verify(signature: ByteArray, data: ByteArray): Boolean {
        return Ed25519.verify(signature, data, key)
    }

    fun toProto(): Protocol.PublicKey {
        return Protocol.PublicKey.newBuilder()
            .setKeyType(keyType)
            .setKey(ByteString.copyFrom(key))
            .build()
    }

    override fun toString(): String {
        return key.toHexString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PublicKey) {
            return false
        }

        if (keyType != other.keyType) {
            return false
        }

        return key.contentEquals(other.key)
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(keyType.hashCode(), key.contentHashCode()))
    }

    companion object {
        fun fromProto(proto: Protocol.PublicKey): PublicKey {
            return PublicKey(proto.keyType, proto.key.toByteArray())
        }
    }
}

class PrivateKey(val keyType: Long, val key: ByteArray) {
    init {
        if (keyType != 1L) {
            throw IllegalArgumentException("unknown key type")
        }

        if (key.size != 32) {
            throw IllegalArgumentException("incorrect private key length")
        }
    }

    fun derivePublicKey(): PublicKey {
        return PublicKey(keyType, Ed25519.privateKeyToPublicKey(key))
    }

    fun toKeyPair(): KeyPair {
        return KeyPair.fromPrivateKey(this)
    }

    fun toProto(): Protocol.PrivateKey {
        return Protocol.PrivateKey.newBuilder()
            .setKeyType(keyType)
            .setKey(ByteString.copyFrom(key))
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PrivateKey) {
            return false
        }

        if (keyType != other.keyType) {
            return false
        }

        return key.contentEquals(other.key)
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(keyType.hashCode(), key.contentHashCode()))
    }

    override fun toString(): String {
        return key.toHexString()
    }

    companion object {
        fun fromProto(proto: Protocol.PrivateKey): PrivateKey {
            return PrivateKey(proto.keyType, proto.key.toByteArray())
        }
    }
}

@Serializable
class Process(@Serializable(with = ByteArraySerializer::class) val process: ByteArray) {
    init {
        if (process.size != 16) {
            throw IllegalArgumentException("incorrect process size")
        }
    }

    fun toProto(): Protocol.Process {
        return Protocol.Process.newBuilder()
            .setProcess(ByteString.copyFrom(process))
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Process) {
            return false
        }

        return process.contentEquals(other.process)
    }

    override fun hashCode(): Int {
        return process.contentHashCode()
    }

    companion object {
        fun fromProto(proto: Protocol.Process): Process {
            return Process(proto.process.toByteArray())
        }

        fun random(): Process {
            return Process(Ed25519.getRandomBytes(16))
        }
    }
}

class Event(
    val system: PublicKey,
    val process: Process,
    val logicalClock: Long,
    val contentType: Long,
    val content: ByteArray,
    val vectorClock: Protocol.VectorClock,
    val lwwElementSet: LWWElementSet?,
    val lwwElement: LWWElement?,
    val references: MutableList<Protocol.Reference>,
    val indices: Protocol.Indices,
    val unixMilliseconds: Long?
) {
    fun toProto(): Protocol.Event {
        val builder = Protocol.Event.newBuilder()
            .setSystem(system.toProto())
            .setProcess(process.toProto())
            .setLogicalClock(logicalClock)
            .setContentType(contentType)
            .setContent(ByteString.copyFrom(content))
            .setVectorClock(vectorClock)
            .setIndices(indices)
            .addAllReferences(references)
            .apply {
                if (this@Event.lwwElementSet != null) {
                    lwwElementSet = this@Event.lwwElementSet.toProto()
                }

                if (this@Event.lwwElement != null) {
                    lwwElement = this@Event.lwwElement.toProto()
                }
            }

        if (unixMilliseconds != null) {
            builder.unixMilliseconds = unixMilliseconds
        }

        return builder.build()
    }

    companion object {
        fun fromProto(proto: Protocol.Event): Event {
            if (!proto.hasSystem()) {
                throw Error("expected system")
            }

            if (!proto.hasProcess()) {
                throw Error("expected process")
            }

            return Event(
                PublicKey.fromProto(proto.system),
                Process.fromProto(proto.process),
                proto.logicalClock,
                proto.contentType,
                proto.content.toByteArray(),
                proto.vectorClock,
                proto.lwwElementSet?.let { LWWElementSet.fromProto(it) },
                proto.lwwElement?.let { LWWElement.fromProto(it) },
                proto.referencesList,
                proto.indices,
                if (proto.hasUnixMilliseconds()) proto.unixMilliseconds else null
            )
        }
    }
}

class SignedEvent(val signature: ByteArray, val rawEvent: ByteArray) {

    val event: Event

    init {
        try {
            val protoEvent = Protocol.Event.parseFrom(rawEvent)
            event = Event.fromProto(protoEvent)

            if (!event.system.verify(signature, rawEvent)) {
                throw IllegalArgumentException("signature verification failed")
            }

        } catch (ex: InvalidProtocolBufferException) {
            throw IllegalArgumentException("error decoding event from proto", ex)
        }
    }

    fun toProto(): Protocol.SignedEvent {
        return Protocol.SignedEvent.newBuilder()
            .setSignature(ByteString.copyFrom(signature))
            .setEvent(ByteString.copyFrom(rawEvent))
            .build()
    }

    fun toPointer(): Pointer {
        val hash = rawEvent.hash()
        return Pointer(event.system, event.process, event.logicalClock, hash)
    }

    companion object {
        fun fromProto(proto: Protocol.SignedEvent): SignedEvent {
            return SignedEvent(proto.signature.toByteArray(), proto.event.toByteArray())
        }
    }
}

class ProcessSecret(val system: KeyPair, val process: Process) {
    fun toProto(): Protocol.StorageTypeProcessSecret {
        return Protocol.StorageTypeProcessSecret.newBuilder()
            .setSystem(system.privateKey.toProto())
            .setProcess(process.toProto())
            .build()
    }

    fun toProcessHandle(): ProcessHandle {
        return ProcessHandle(this, system.publicKey)
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(system.hashCode(), process.hashCode()))
    }

    override fun toString(): String {
        return "(system: $system, process: $process)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ProcessSecret) {
            return false
        }

        return system == other.system && process == other.process
    }

    companion object {
        fun fromProto(proto: Protocol.StorageTypeProcessSecret): ProcessSecret {
            if (!proto.hasSystem()) {
                throw Error("expected system")
            }

            if (!proto.hasProcess()) {
                throw Error("expected process")
            }

            return ProcessSecret(
                PrivateKey.fromProto(proto.system).toKeyPair(),
                Process.fromProto(proto.process),
            )
        }
    }
}

class LWWElement(val value: ByteArray, val unixMilliseconds: Long) {
    companion object {
        fun fromProto(proto: Protocol.LWWElement): LWWElement {
            return LWWElement(proto.value.toByteArray(), proto.unixMilliseconds)
        }
    }

    fun toProto(): Protocol.LWWElement {
        return Protocol.LWWElement.newBuilder()
            .setValue(ByteString.copyFrom(value))
            .setUnixMilliseconds(unixMilliseconds)
            .build()
    }
}

enum class LWWElementSetOperation {
    ADD,
    REMOVE,
}

fun LWWElementSetOperation.toProto(): Protocol.LWWElementSet.Operation {
    return when (this) {
        LWWElementSetOperation.ADD -> Protocol.LWWElementSet.Operation.ADD
        LWWElementSetOperation.REMOVE -> Protocol.LWWElementSet.Operation.REMOVE
    }
}

fun Protocol.LWWElementSet.Operation.toInternal(): LWWElementSetOperation {
    return when (this) {
        Protocol.LWWElementSet.Operation.ADD -> LWWElementSetOperation.ADD
        Protocol.LWWElementSet.Operation.REMOVE -> LWWElementSetOperation.REMOVE
        else -> throw Error("unknown LWWElementSetOperation")
    }
}

class LWWElementSet(val operation: LWWElementSetOperation, val value: ByteArray, val unixMilliseconds: Long) {
    fun toProto(): Protocol.LWWElementSet {
        return Protocol.LWWElementSet.newBuilder()
            .setOperation(operation.toProto())
            .setValue(ByteString.copyFrom(value))
            .setUnixMilliseconds(unixMilliseconds)
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.LWWElementSet): LWWElementSet {
            return LWWElementSet(
                proto.operation.toInternal(),
                proto.value.toByteArray(),
                proto.unixMilliseconds,
            )
        }
    }
}

data class KeyPair(val privateKey: PrivateKey, val publicKey: PublicKey) {
    val secretKey: ByteArray

    init {
        if (privateKey.keyType != publicKey.keyType) {
            throw IllegalArgumentException("private key and public key key types must match")
        }

        if (privateKey.keyType != 1L) {
            throw IllegalArgumentException("unknown key type")
        }

        if (privateKey.key.size != 32) {
            throw IllegalArgumentException("incorrect private key length")
        }

        if (publicKey.key.size != 32) {
            throw IllegalArgumentException("incorrect public key length")
        }

        secretKey = privateKey.key + publicKey.key
    }

    fun sign(data: ByteArray): ByteArray {
        return Ed25519.sign(data, this)
    }

    fun verify(signature: ByteArray, data: ByteArray): Boolean {
        return publicKey.verify(signature, data)
    }

    fun toProto(): Protocol.KeyPair {
        return Protocol.KeyPair.newBuilder()
            .setKeyType(privateKey.keyType)
            .setPrivateKey(ByteString.copyFrom(privateKey.key))
            .setPublicKey(ByteString.copyFrom(publicKey.key))
            .build()
    }

    override fun toString(): String {
        return "(privateKey: $privateKey, publicKey: $publicKey)"
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(privateKey.hashCode(), publicKey.hashCode()))
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KeyPair) {
            return false
        }

        return privateKey == other.privateKey && publicKey == other.publicKey
    }

    companion object {
        fun fromProto(proto: Protocol.KeyPair): KeyPair {
            return KeyPair(
                PrivateKey(proto.keyType, proto.privateKey.toByteArray()),
                PublicKey(proto.keyType, proto.publicKey.toByteArray()),
            )
        }

        fun fromPrivateKey(privateKey: PrivateKey): KeyPair {
            return KeyPair(
                privateKey,
                privateKey.derivePublicKey()
            )
        }

        fun random(): KeyPair {
            return Ed25519.generateKeyPair()
        }
    }
}

data class StorageTypeCRDTSetItem(
    val contentType: Long,
    val value: ByteArray,
    var unixMilliseconds: Long,
    var operation: LWWElementSetOperation
) {
    fun toProto(): Protocol.StorageTypeCRDTSetItem {
        return Protocol.StorageTypeCRDTSetItem.newBuilder()
            .setContentType(contentType)
            .setValue(ByteString.copyFrom(value))
            .setUnixMilliseconds(unixMilliseconds)
            .setOperation(operation.toProto())
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.StorageTypeCRDTSetItem): StorageTypeCRDTSetItem {
            return StorageTypeCRDTSetItem(
                proto.contentType,
                proto.value.toByteArray(),
                proto.unixMilliseconds,
                proto.operation.toInternal()
            )
        }
    }
}

data class StorageTypeCRDTItem(
    val contentType: Long,
    var value: ByteArray,
    var unixMilliseconds: Long
) {
    fun toProto(): Protocol.StorageTypeCRDTItem {
        return Protocol.StorageTypeCRDTItem.newBuilder()
            .setContentType(contentType)
            .setValue(ByteString.copyFrom(value))
            .setUnixMilliseconds(unixMilliseconds)
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.StorageTypeCRDTItem): StorageTypeCRDTItem {
            return StorageTypeCRDTItem(
                proto.contentType,
                proto.value.toByteArray(),
                proto.unixMilliseconds
            )
        }
    }
}

data class StorageTypeSystemState(
    val crdtSetItems: MutableList<StorageTypeCRDTSetItem>,
    val processes: MutableList<Process>,
    val crdtItems: MutableList<StorageTypeCRDTItem>
) {
    fun update(event: Event) {
        event.lwwElementSet?.let { lwwElementSet ->
            val foundIndex = crdtSetItems.indexOfFirst { item ->
                item.contentType == event.contentType && item.value.contentEquals(lwwElementSet.value)
            }

            var found = false
            if (foundIndex != -1) {
                val foundItem = crdtSetItems[foundIndex]
                if (foundItem.unixMilliseconds < lwwElementSet.unixMilliseconds) {
                    foundItem.operation = lwwElementSet.operation
                    foundItem.unixMilliseconds = lwwElementSet.unixMilliseconds
                }
                found = true
            }

            if (!found) {
                crdtSetItems.add(StorageTypeCRDTSetItem(event.contentType, lwwElementSet.value, lwwElementSet.unixMilliseconds, lwwElementSet.operation))
            }
        }

        event.lwwElement?.let { lwwElement ->
            val matchingItems = crdtItems.filter { it.contentType == event.contentType }
            val lastItem = matchingItems.maxByOrNull { it.unixMilliseconds }
            if (matchingItems.size > 1) {
                for (item in matchingItems) {
                    if (item == lastItem) {
                        continue
                    }

                    crdtItems.remove(item)
                }
            }

            if (lastItem != null) {
                if (lastItem.unixMilliseconds < lwwElement.unixMilliseconds) {
                    crdtItems.remove(lastItem)
                    crdtItems.add(StorageTypeCRDTItem(event.contentType, lwwElement.value, lwwElement.unixMilliseconds))
                } else {

                }
            } else {
                crdtItems.add(StorageTypeCRDTItem(event.contentType, lwwElement.value, lwwElement.unixMilliseconds))
            }
        }

        var foundProcess = false
        for (rawProcess in processes) {
            if (rawProcess == event.process) {
                foundProcess = true
                break
            }
        }

        if (!foundProcess) {
            processes.add(event.process)
        }
    }

    fun toProto(): Protocol.StorageTypeSystemState {
        return Protocol.StorageTypeSystemState.newBuilder()
            .addAllCrdtSetItems(crdtSetItems.map { i -> i.toProto() })
            .addAllProcesses(processes.map { i -> i.toProto() })
            .addAllCrdtItems(crdtItems.map { i -> i.toProto() })
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.StorageTypeSystemState): StorageTypeSystemState {
            val crdtSetItems = proto.crdtSetItemsList
                .map { i -> StorageTypeCRDTSetItem.fromProto(i) }
                .groupBy { it.contentType to it.value.toBase64() }
                .map { entry -> entry.value.maxBy { it.unixMilliseconds } }
                .toMutableList()

            return StorageTypeSystemState(
                crdtSetItems,
                proto.processesList.map { i -> Process.fromProto(i) }.toMutableList(),
                proto.crdtItemsList.map { i -> StorageTypeCRDTItem.fromProto(i) }.groupBy { i -> i.contentType }.map { i -> i.value.maxBy { it -> it.unixMilliseconds } }.toMutableList()
            )
        }

        fun create(): StorageTypeSystemState {
            return StorageTypeSystemState(mutableListOf(), mutableListOf(), mutableListOf())
        }
    }
}

data class StorageTypeProcessSecret(
   val system: PrivateKey,
   val process: Process
) {
    fun toProto(): Protocol.StorageTypeProcessSecret {
        return Protocol.StorageTypeProcessSecret.newBuilder()
            .setSystem(system.toProto())
            .setProcess(process.toProto())
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.StorageTypeProcessSecret): StorageTypeProcessSecret {
            return StorageTypeProcessSecret(
                PrivateKey.fromProto(proto.system),
                Process.fromProto(proto.process)
            )
        }
    }
}

data class StorageTypeProcessState(
    var logicalClock: Long,
    val ranges: MutableList<Range>,
    val indices: Indices?
) {
    fun update(event: Event) {
        Ranges.insert(ranges, event.logicalClock)

        if (event.logicalClock.compareTo(logicalClock) == 1) {
            logicalClock = event.logicalClock
        }

        var foundIndex = false
        for (index in indices!!.indices) {
            if (index.indexType == event.contentType) {
                foundIndex = true

                if (event.logicalClock.compareTo(index.logicalClock) == 1) {
                    index.logicalClock = event.logicalClock
                }
            }
        }

        if (!foundIndex) {
            indices.indices.add(Index(event.contentType, event.logicalClock))
        }
    }

    fun toProto(): Protocol.StorageTypeProcessState {
        val builder = Protocol.StorageTypeProcessState.newBuilder()
            .setLogicalClock(logicalClock)
            .addAllRanges(ranges.map { i -> i.toProto() })

        if (indices != null) {
            builder.indices = indices.toProto()
        }

        return builder.build()
    }

    companion object {
        fun fromProto(proto: Protocol.StorageTypeProcessState): StorageTypeProcessState {
            return StorageTypeProcessState(
                proto.logicalClock,
                proto.rangesList.map { i -> Range.fromProto(i) }.toMutableList(),
                Indices.fromProto(proto.indices)
            )
        }

        fun create(): StorageTypeProcessState {
            return StorageTypeProcessState(0, mutableListOf(), Indices(mutableListOf()))
        }
    }
}

data class Range(
    val low: Long,
    val high: Long
) {
    fun toProto(): Protocol.Range {
        return Protocol.Range.newBuilder()
            .setLow(low)
            .setHigh(high)
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.Range): Range {
            return Range(proto.low, proto.high)
        }
    }
}

data class Indices(
    val indices: MutableList<Index>
) {
    fun toProto(): Protocol.Indices {
        return Protocol.Indices.newBuilder()
            .addAllIndices(indices.map { it.toProto() })
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.Indices): Indices {
            return Indices(proto.indicesList.map { Index.fromProto(it) }.toMutableList())
        }
    }
}

data class Index(
    val indexType: Long,
    var logicalClock: Long
) {
    fun toProto(): Protocol.Index {
        return Protocol.Index.newBuilder()
            .setIndexType(indexType)
            .setLogicalClock(logicalClock)
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.Index): Index {
            return Index(proto.indexType, proto.logicalClock)
        }
    }
}

data class RangesForProcess(
    val process: Process,
    val ranges: MutableList<Range>
) {
    fun toProto(): Protocol.RangesForProcess {
        return Protocol.RangesForProcess.newBuilder()
            .setProcess(process.toProto())
            .addAllRanges(ranges.map { i -> i.toProto() })
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.RangesForProcess): RangesForProcess {
            return RangesForProcess(
                Process.fromProto(proto.process),
                proto.rangesList.map { i -> Range.fromProto(i) }.toMutableList()
            )
        }
    }
}

data class RangesForSystem(
    val rangesForProcesses: MutableList<RangesForProcess>
) {
    fun toProto(): Protocol.RangesForSystem {
        return Protocol.RangesForSystem.newBuilder()
            .addAllRangesForProcesses(rangesForProcesses.map { i -> i.toProto() })
            .build()
    }

    companion object {
        fun fromProto(proto: Protocol.RangesForSystem): RangesForSystem {
            return RangesForSystem(proto.rangesForProcessesList.map { i -> RangesForProcess.fromProto(i) }.toMutableList())
        }
    }
}

class Models {
    companion object {
        fun referenceFromBuffer(buffer: ByteArray): Protocol.Reference {
            return Protocol.Reference.newBuilder()
                .setReferenceType(3)
                .setReference(ByteString.copyFrom(buffer))
                .build()
        }
    }
}
