package com.futo.polycentric.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import userpackage.Protocol

class SqlLiteDbHelper : SQLiteOpenHelper {
    constructor(context: Context, factory: SQLiteDatabase.CursorFactory? = null) : super(context, DATABASE_NAME, factory, DATABASE_VERSION) { }

    override fun onCreate(db: SQLiteDatabase) {
        Log.i(TAG, "onCreate")
        createTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrade from version $oldVersion to $newVersion")
        Log.w(TAG, "This is version 1, no DB to update")

        var currentVersion = oldVersion
        if (oldVersion <= 1 && newVersion == 2) {
            deleteTables(db)
            createTables(db)
            currentVersion = 2
        }

        if (currentVersion == 2 && newVersion == 3) {
            upgradeOldSecrets(db)
            currentVersion = 3
        }

        if (currentVersion == 3 && newVersion == 4) {
            db.execSQL("""
                CREATE TABLE new_signed_events (
                    id INTEGER PRIMARY KEY, 
                    public_key VARCHAR, 
                    process VARCHAR, 
                    logical_clock INTEGER, 
                    content_type INTEGER, 
                    value BLOB,
                    UNIQUE(public_key, process, logical_clock)
                )
            """)

            db.execSQL("""
                INSERT INTO new_signed_events (id, public_key, process, logical_clock, content_type, value)
                SELECT MAX(id) as id, public_key, process, logical_clock, content_type, value 
                FROM signed_events
                GROUP BY public_key, process, logical_clock
            """)

            db.execSQL("DROP TABLE signed_events")
            db.execSQL("ALTER TABLE new_signed_events RENAME TO signed_events")
            db.execSQL("CREATE INDEX idx_event_pointer ON signed_events (public_key, process, logical_clock)")
        }
    }

    fun upgradeOldSecrets(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT value FROM process_secrets", arrayOf())
        val secrets = arrayListOf<ProcessSecret>()

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val value = it.getBlob(0)
                    if (value != null) {
                        try {
                            val secret = ProcessSecret.fromProto(Protocol.StorageTypeProcessSecret.parseFrom(PEncryptionProviderV0.instance.decrypt(value)))
                            Log.i(TAG, "Upgrading old secret system = ${secret.system.publicKey.key.toBase64()}")
                            secrets.add(secret)
                        } catch (e: Throwable) {
                            Log.i(TAG, "Failed to convert secret, assuming it is V1 already and skipping", e)
                        }
                    }
                } while (cursor.moveToNext())
            }
        }

        db.beginTransaction();
        try {
            for (secret in secrets) {
                db.execSQL("DELETE FROM process_secrets WHERE public_key = ?", arrayOf(secret.system.publicKey.key.toBase64()))
                val value = PEncryptionProviderV1.instance.encrypt(secret.toProto().toByteArray())
                db.execSQL("INSERT INTO process_secrets VALUES(?, ?)", arrayOf(secret.system.publicKey.key.toBase64(), value))
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE system_states (public_key VARCHAR PRIMARY KEY, value BLOB)")
        db.execSQL("CREATE TABLE process_states (public_key_process VARCHAR PRIMARY KEY, value BLOB)")
        db.execSQL("CREATE TABLE process_secrets (public_key VARCHAR PRIMARY KEY, value BLOB)")
        db.execSQL("CREATE TABLE signed_events (id INTEGER PRIMARY KEY, public_key VARCHAR, process VARCHAR, logical_clock INTEGER, content_type INTEGER, value BLOB, UNIQUE(public_key, process, logical_clock))")
        db.execSQL("CREATE INDEX idx_event_pointer ON signed_events (public_key, process, logical_clock)")
    }

    private fun deleteTables(db: SQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS idx_event_pointer")
        db.execSQL("DROP TABLE IF EXISTS system_states")
        db.execSQL("DROP TABLE IF EXISTS process_states")
        db.execSQL("DROP TABLE IF EXISTS process_secrets")
        db.execSQL("DROP TABLE IF EXISTS signed_events")
    }

    fun recreate() {
        Log.i(TAG, "clearDbAndRecreate")

        val db = writableDatabase
        deleteTables(db)
        createTables(db)
    }

    companion object {
        private const val TAG = "SqlLiteDbHelper"
        private const val DATABASE_NAME = "polycentric_core.db"
        private const val DATABASE_VERSION = 4
    }
}