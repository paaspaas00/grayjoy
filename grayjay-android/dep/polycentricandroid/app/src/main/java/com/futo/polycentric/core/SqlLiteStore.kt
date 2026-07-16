package com.futo.polycentric.core

import android.util.Log
import userpackage.Protocol

class SqlLiteStore(private val _db: SqlLiteDbHelper) : Store() {
    init {
        Log.i(TAG, "SQLite store initialized.")
    }

    override fun getSystemState(system: PublicKey): StorageTypeSystemState {
        val cursor = _db.readableDatabase.rawQuery("SELECT value FROM system_states WHERE public_key = ?", arrayOf(system.key.toBase64()))

        var value: ByteArray? = null
        cursor.use {
            if (it.moveToFirst()) {
                value = it.getBlob(0)
            }
        }

        if (value != null) {
            return StorageTypeSystemState.fromProto(Protocol.StorageTypeSystemState.parseFrom(value))
        }

        return StorageTypeSystemState.create()
    }

    override fun updateSystemState(system: PublicKey, storageTypeSystemState: StorageTypeSystemState) {
        _db.writableDatabase.execSQL("INSERT OR REPLACE INTO system_states VALUES(?, ?)",
            arrayOf(system.key.toBase64(), storageTypeSystemState.toProto().toByteArray()))
    }

    override fun getProcessState(system: PublicKey, process: Process): StorageTypeProcessState {
        val cursor = _db.readableDatabase.rawQuery("SELECT value FROM process_states WHERE public_key_process = ?", arrayOf((system.key + process.process).toBase64()))

        var value: ByteArray? = null
        cursor.use {
            if (it.moveToFirst()) {
                value = it.getBlob(0)
            }
        }

        if (value != null) {
            return StorageTypeProcessState.fromProto(Protocol.StorageTypeProcessState.parseFrom(value))
        }

        return StorageTypeProcessState.create()
    }

    override fun updateProcessState(system: PublicKey, process: ByteArray, storageTypeProcessState: StorageTypeProcessState) {
        _db.writableDatabase.execSQL("INSERT OR REPLACE INTO process_states VALUES(?, ?)",
            arrayOf((system.key + process).toBase64(), storageTypeProcessState.toProto().toByteArray()))
    }

    override fun putTombstone(system: PublicKey, process: ByteArray, logicalClock: Long, mutationPointer: Pointer) {
        val storageTypeEvent = Protocol.StorageTypeEvent.newBuilder()
            .setMutationPointer(mutationPointer.toProto())
            .build()

        _db.writableDatabase.execSQL("REPLACE INTO signed_events(public_key, process, logical_clock, content_type, value) VALUES(?, ?, ?, 0, ?)",
            arrayOf(system.key.toBase64(), process.toBase64(), logicalClock.toString(), storageTypeEvent.toByteArray()))
    }

    override fun getSignedEvent(system: PublicKey, process: Process, logicalClock: Long): SignedEvent? {
        val cursor = _db.readableDatabase.rawQuery("SELECT value FROM signed_events WHERE public_key = ? AND process = ? AND logical_clock = ?",
            arrayOf(system.key.toBase64(), process.process.toBase64(), logicalClock.toString()))

        var value: ByteArray? = null
        cursor.use {
            if (it.moveToFirst()) {
                value = it.getBlob(0)
            }
        }

        if (value != null) {
            val storageTypeEvent = Protocol.StorageTypeEvent.parseFrom(value)
            if (!storageTypeEvent.hasEvent()) {
                return null
            }

            return SignedEvent.fromProto(storageTypeEvent.event)
        }

        return null
    }

    override fun enumerateSignedEvents(system: PublicKey, contentType: ContentType?, handler: (SignedEvent) -> Unit) {
        val cursor = if (contentType == null) {
            _db.readableDatabase.rawQuery("SELECT value FROM signed_events WHERE public_key = ?",
                arrayOf(system.key.toBase64()))
        } else {
            _db.readableDatabase.rawQuery("SELECT value FROM signed_events WHERE public_key = ? AND content_type = ?",
                arrayOf(system.key.toBase64(), contentType.value.toString()))
        }

        cursor.use {
            if (!it.moveToFirst()) {
                return
            }

            do {
                val value = it.getBlob(0)
                val storageTypeEvent = Protocol.StorageTypeEvent.parseFrom(value)
                if (!storageTypeEvent.hasEvent()) {
                    continue
                }

                handler(SignedEvent.fromProto(storageTypeEvent.event))
            } while (it.moveToNext())
        }
    }

    override fun putSignedEvent(event: SignedEvent) {
        val storageTypeEvent = Protocol.StorageTypeEvent.newBuilder()
            .setEvent(event.toProto())
            .build()

        _db.writableDatabase.execSQL("REPLACE INTO signed_events(public_key, process, logical_clock, content_type, value) VALUES(?, ?, ?, ?, ?)",
            arrayOf(event.event.system.key.toBase64(), event.event.process.process.toBase64(), event.event.logicalClock.toString(), event.event.contentType.toString(), storageTypeEvent.toByteArray()))
    }

    override fun getProcessSecret(system: PublicKey): ProcessSecret? {
        val cursor = _db.readableDatabase.rawQuery("SELECT value FROM process_secrets WHERE public_key = ?", arrayOf(system.key.toBase64()))

        var value: ByteArray? = null
        cursor.use {
            if (it.moveToFirst()) {
                value = it.getBlob(0)
            }
        }

        if (value != null) {
            return ProcessSecret.fromProto(Protocol.StorageTypeProcessSecret.parseFrom(PEncryptionProvider.instance.decrypt(value!!)))
        }

        return null
    }

    override fun getProcessSecrets(): List<ProcessSecret> {
        val cursor = _db.readableDatabase.rawQuery("SELECT value FROM process_secrets", arrayOf())
        val secrets = arrayListOf<ProcessSecret>()

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val value = it.getBlob(0)
                    if (value != null) {
                        val secret = ProcessSecret.fromProto(Protocol.StorageTypeProcessSecret.parseFrom(PEncryptionProvider.instance.decrypt(value)))
                        secrets.add(secret)
                    }
                } while (cursor.moveToNext())
            }
        }

        return secrets
    }

    override fun removeProcessSecret(system: PublicKey) {
        _db.writableDatabase.execSQL("DELETE FROM process_secrets WHERE public_key = ?", arrayOf(system.key.toBase64()))
    }

    override fun addProcessSecret(processSecret: ProcessSecret) {
        val value = PEncryptionProvider.instance.encrypt(processSecret.toProto().toByteArray())
        _db.writableDatabase.execSQL("INSERT INTO process_secrets VALUES(?, ?)",
            arrayOf(processSecret.system.publicKey.key.toBase64(), value))
    }

    companion object {
        private const val TAG = "SqlLiteStore"
    }
}