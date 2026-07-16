package com.futo.polycentric.core

import android.util.Log
import userpackage.Protocol
import userpackage.Protocol.StorageTypeEvent

class MemoryStore : Store() {
    private data class EventKey(val system: PublicKey, val process: ByteArray, val logicalClock: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EventKey) return false

            if (system != other.system) return false
            if (!process.contentEquals(other.process)) return false
            if (logicalClock != other.logicalClock) return false

            return true
        }

        override fun hashCode(): Int {
            return combineHashCodes(listOf(system.hashCode(), process.contentHashCode(), logicalClock.hashCode()))
        }
    }

    private val _systemStates: HashMap<PublicKey, StorageTypeSystemState> = hashMapOf()
    private val _processStates: HashMap<Process, StorageTypeProcessState> = hashMapOf()
    private val _processSecrets: HashMap<PublicKey, ProcessSecret> = hashMapOf()
    private val _signedEvents: HashMap<EventKey, StorageTypeEvent> = hashMapOf()

    init {
        Log.i(TAG, "Memory store initialized.")
    }

    override fun getSystemState(system: PublicKey): StorageTypeSystemState {
        var systemState = _systemStates[system]
        if (systemState == null) {
            systemState = StorageTypeSystemState.create()
            _systemStates[system] = systemState
        }

        return systemState
    }

    override fun updateSystemState(system: PublicKey, storageTypeSystemState: StorageTypeSystemState) { }

    override fun getProcessState(system: PublicKey, process: Process): StorageTypeProcessState {
        var processState = _processStates[process]
        if (processState == null) {
            processState = StorageTypeProcessState.create()
            _processStates[process] = processState
        }

        return processState
    }

    override fun updateProcessState(system: PublicKey, process: ByteArray, storageTypeProcessState: StorageTypeProcessState) { }

    override fun getSignedEvent(system: PublicKey, process: Process, logicalClock: Long): SignedEvent? {
        val e = _signedEvents[EventKey(system, process.process, logicalClock)] ?: return null
        if (!e.hasEvent()) {
            return null
        }

        return SignedEvent.fromProto(e.event)
    }

    override fun enumerateSignedEvents(system: PublicKey, contentType: ContentType?, handler: (SignedEvent) -> Unit) {
        for (pair in _signedEvents) {
            if (!pair.value.hasEvent()) {
                continue
            }

            if (pair.key.system != system) {
                continue
            }

            val e = pair.value.event ?: continue
            val se = SignedEvent.fromProto(e)
            if (contentType == null || se.event.contentType == contentType.value) {
                handler(se)
            }
        }
    }

    override fun putTombstone(system: PublicKey, process: ByteArray, logicalClock: Long, mutationPointer: Pointer) {
        _signedEvents[EventKey(system, process, logicalClock)] = StorageTypeEvent.newBuilder()
            .setMutationPointer(mutationPointer.toProto())
            .build()
    }

    override fun putSignedEvent(event: SignedEvent) {
        _signedEvents[EventKey(event.event.system, event.event.process.process, event.event.logicalClock)] = StorageTypeEvent.newBuilder()
            .setEvent(event.toProto())
            .build()
    }

    override fun getProcessSecret(system: PublicKey): ProcessSecret? {
        return _processSecrets[system]
    }

    override fun addProcessSecret(processSecret: ProcessSecret) {
        _processSecrets[processSecret.system.publicKey] = processSecret
    }

    override fun getProcessSecrets(): List<ProcessSecret> {
        return _processSecrets.values.toList()
    }

    override fun removeProcessSecret(system: PublicKey) {
        _processSecrets.remove(system)
    }

    fun clear() {
        _systemStates.clear()
        _processSecrets.clear()
        _signedEvents.clear()
        _processStates.clear()
    }

    companion object {
        private const val TAG = "MemoryStore"
    }
}