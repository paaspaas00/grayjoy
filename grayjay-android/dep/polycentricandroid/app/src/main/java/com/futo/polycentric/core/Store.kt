package com.futo.polycentric.core

import userpackage.Protocol

abstract class Store {
    abstract fun getSystemState(system: PublicKey): StorageTypeSystemState
    abstract fun updateSystemState(system: PublicKey, storageTypeSystemState: StorageTypeSystemState)
    abstract fun getProcessState(system: PublicKey, process: Process): StorageTypeProcessState
    abstract fun updateProcessState(system: PublicKey, process: ByteArray, storageTypeProcessState: StorageTypeProcessState)
    abstract fun putTombstone(system: PublicKey, process: ByteArray, logicalClock: Long, mutationPointer: Pointer)
    abstract fun putSignedEvent(event: SignedEvent)
    abstract fun getProcessSecret(system: PublicKey): ProcessSecret?
    abstract fun getProcessSecrets(): List<ProcessSecret>
    abstract fun removeProcessSecret(system: PublicKey)
    abstract fun addProcessSecret(processSecret: ProcessSecret)
    abstract fun getSignedEvent(system: PublicKey, process: Process, logicalClock: Long): SignedEvent?
    abstract fun enumerateSignedEvents(system: PublicKey, contentType: ContentType? = null, handler: (SignedEvent) -> Unit)
    open fun cleanup() {}

    fun getSignedEvents(system: PublicKey, process: Process, ranges: List<Protocol.Range>): List<SignedEvent> {
        val ses = arrayListOf<SignedEvent>()
        for (range in ranges) {
            for (logicalClock in range.low .. range.high) {
                val se = getSignedEvent(system, process, logicalClock) ?: continue
                ses.add(se)
            }
        }

        return ses
    }

    fun getSignedEvent(pointer: Pointer): SignedEvent? {
        return getSignedEvent(pointer.system, pointer.process, pointer.logicalClock)
    }

    companion object {
        lateinit var instance: Store

        fun initializeSqlLiteStore(db: SqlLiteDbHelper) {
            instance = SqlLiteStore(db)
        }

        fun initializeMemoryStore() {
            instance = MemoryStore()
        }

        fun cleanup() {
            instance.cleanup()
        }
    }
}