package com.futo.polycentric.core

import userpackage.Protocol
import java.lang.Integer.min
import java.util.Date

class ProcessHandle constructor(
    val processSecret: ProcessSecret,
    val system: PublicKey,
    private var _listener: ((signedEvent: SignedEvent) -> Unit)? = null
) {
    fun setListener(listener: (signedEvent: SignedEvent) -> Unit) {
        this._listener = listener
    }

    fun post(content: String, reference: Protocol.Reference? = null): Pointer {
        val builder = Protocol.Post.newBuilder()
            .setContent(content)

        return publish(
            ContentType.POST.value,
            builder.build().toByteArray(),
            null,
            null,
            if (reference != null) arrayListOf(reference) else arrayListOf()
        )
    }

    fun opinion(reference: Protocol.Reference, opinion: Opinion): Pointer {
        return publish(
            ContentType.OPINION.value,
            ByteArray(0),
            null,
            LWWElement(opinion.data, System.currentTimeMillis()),
            arrayListOf(reference)
        )
    }

    fun setCRDTItem(contentType: Long, value: ByteArray): Pointer {
        return publish(
            contentType,
            ByteArray(0),
            null,
            LWWElement(
                value,
                Date().time
            ),
            mutableListOf()
        )
    }

    fun setCRDTElementSetItem(contentType: Long, value: ByteArray, operation: LWWElementSetOperation): Pointer {
        return publish(
            contentType,
            ByteArray(0),
            LWWElementSet(
                operation,
                value,
                Date().time
            ),
            null,
            mutableListOf()
        )

    }

    fun setUsername(username: String): Pointer {
        return setCRDTItem(
            ContentType.USERNAME.value,
            username.toByteArray()
        )
    }

    fun setDescription(description: String): Pointer {
        return setCRDTItem(
            ContentType.DESCRIPTION.value,
            description.toByteArray(),
        )
    }

    fun setStore(storeLink: String): Pointer {
        return setCRDTItem(
            ContentType.STORE.value,
            storeLink.toByteArray(),
        )
    }

    fun setBanner(banner: Pointer): Pointer {
        return setCRDTItem(
            ContentType.BANNER.value,
            banner.toProto().toByteArray(),
        )
    }

    fun setAvatar(avatar: Protocol.ImageBundle): Pointer {
        return setCRDTItem(
            ContentType.AVATAR.value,
            avatar.toByteArray(),
        )
    }

    fun addServer(server: String): Pointer {
        return setCRDTElementSetItem(
            ContentType.SERVER.value,
            server.toByteArray(),
            LWWElementSetOperation.ADD
        );
    }

    fun removeServer(server: String): Pointer {
        return setCRDTElementSetItem(
            ContentType.SERVER.value,
            server.toByteArray(),
            LWWElementSetOperation.REMOVE
        );
    }

    fun addAuthority(server: String): Pointer {
        return setCRDTElementSetItem(
            ContentType.AUTHORITY.value,
            server.toByteArray(),
            LWWElementSetOperation.ADD
        );
    }

    fun removeAuthority(server: String): Pointer {
        return setCRDTElementSetItem(
            ContentType.AUTHORITY.value,
            server.toByteArray(),
            LWWElementSetOperation.REMOVE
        );
    }

    fun vouch(pointer: Pointer): Pointer {
        return publish(
            ContentType.VOUCH.value,
            ByteArray(0),
            null,
            null,
            mutableListOf(pointer.toReference())
        )
    }

    fun claim(claimValue: Protocol.Claim): Pointer {
        return publish(
            ContentType.CLAIM.value,
            claimValue.toByteArray(),
            null,
            null,
            mutableListOf()
        )
    }

    fun delete(process: Process, logicalClock: Long): Pointer? {
        val ev = Store.instance.getSignedEvent(system, process, logicalClock) ?: return null

        var content = Protocol.Delete.newBuilder()
            .setProcess(process.toProto())
            .setLogicalClock(logicalClock)
            .setIndices(ev.event.indices)
            .setContentType(ev.event.contentType)

        if (ev.event.unixMilliseconds != null) {
            content.setUnixMilliseconds(ev.event.unixMilliseconds)
        }

        return publish(
            ContentType.DELETE.value,
            content.build().toByteArray(),
            null,
            null,
            mutableListOf()
        )
    }

    fun publishBlob(content: ByteArray): List<Range> {
        val maxBytes = 1024 * 512
        var i = 0
        val ranges = mutableListOf<Range>()

        while (true) {
            if (i >= content.size - 1) {
                break
            }

            val end = min(i + maxBytes, content.size - 1)
            val bytesToUpload = content.sliceArray(IntRange(i, end))
            val pointer = publish(
                ContentType.BLOB_SECTION.value,
                bytesToUpload,
                null,
                null,
                mutableListOf()
            )

            Ranges.insert(ranges, pointer.logicalClock)
            i = end + 1
        }

        return ranges
    }

    private fun publish(contentType: Long, content: ByteArray, lwwElementSet: LWWElementSet?, lwwElement: LWWElement?, references: MutableList<Protocol.Reference>): Pointer {
        synchronized(this) {
            val processState = Store.instance.getProcessState(system, processSecret.process)
            val event = Event(
                system,
                processSecret.process,
                processState.logicalClock + 1L,
                contentType,
                content,
                Protocol.VectorClock.newBuilder().addAllLogicalClocks(listOf()).build(),
                lwwElementSet,
                lwwElement,
                references,
                processState.indices!!.toProto(),
                unixMilliseconds = System.currentTimeMillis()
            )

            val eventBuffer = event.toProto().toByteArray()
            val signedEvent = SignedEvent(processSecret.system.sign(eventBuffer), eventBuffer)

            return ingest(signedEvent)
        }
    }

    fun ingest(signedEvent: SignedEvent): Pointer {
        val event = signedEvent.event

        val systemState = Store.instance.getSystemState(event.system)
        val processState = Store.instance.getProcessState(system, event.process)

        if (event.contentType == ContentType.DELETE.value) {
            val deleteProto = Protocol.Delete.parseFrom(event.content)
            if (!deleteProto.hasProcess()) {
                throw Error("Delete expected process")
            }

            val deleteProcess = Process.fromProto(deleteProto.process)
            val deleteProcessState = if (event.process != deleteProcess) {
                Store.instance.getProcessState(event.system, deleteProcess)
            } else {
                processState
            }

            Ranges.insert(deleteProcessState.ranges, deleteProto.logicalClock)
            Store.instance.putTombstone(event.system, deleteProcess.process, deleteProto.logicalClock, signedEvent.toPointer())
        }

        systemState.update(event)
        Store.instance.updateSystemState(event.system, systemState)

        processState.update(event)
        Store.instance.updateProcessState(system, event.process.process, processState)

        Store.instance.putSignedEvent(signedEvent)
        this._listener?.invoke(signedEvent)

        return signedEvent.toPointer()
    }

    suspend fun fullyBackfillServers(): Map<String, Throwable> {
        return Synchronization.fullyBackFillServers(this, system)
    }

    suspend fun fullyBackfillClient(server: String) {
        Synchronization.fullyBackFillClient(this, system, server)
    }

    companion object {
        fun create(): ProcessHandle {
            val keyPair = KeyPair.random()
            val process = Process.random()
            val processSecret = ProcessSecret(keyPair, process)
            return ProcessHandle(processSecret, processSecret.system.publicKey)
        }
    }
}
