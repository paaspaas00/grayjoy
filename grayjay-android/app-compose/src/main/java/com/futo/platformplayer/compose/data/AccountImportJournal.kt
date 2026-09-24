package com.futo.platformplayer.compose.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A write-ahead copy lets the next process roll back a kill between preference-file writes. */
internal class AccountImportJournal(context: Context, profileId: String) {
    private val app = context.applicationContext
    private val profileLock = profileDataLock(context, profileId)
    private val suffix = if (profileId == "main") "" else "_$profileId"
    private val profileKey = MessageDigest.getInstance("SHA-256")
        .digest(profileId.toByteArray()).joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(app.filesDir, "account-import/$profileKey.json"))
    private val quarantineMarker = AtomicFile(File(app.filesDir, "account-import/$profileKey.quarantining"))
    private val names = listOf(
        "grayjay_compose_library_v2$suffix",
        "grayjay_compose_watch_progress_v1$suffix",
        "grayjay_compose_preferences$suffix",
    )
    private val selectedKeys = setOf("followed_creator_ids", "following_initialized", "imported_channels")

    fun begin() = synchronized(profileLock) {
        ensureWritable()
        check(file.baseFile.absolutePath !in active) { "An import is already being committed" }
        val stores = JSONArray()
        names.forEachIndexed { index, name ->
            val values = JSONObject()
            app.getSharedPreferences(name, 0).all.forEach { (key, value) ->
                if (index != 2 || key in selectedKeys) {
                    values.put(key, JSONObject().apply {
                        put("type", when (value) {
                            is Set<*> -> "set"
                            is Boolean -> "boolean"
                            is Float -> "float"
                            is Long -> "long"
                            is Int -> "int"
                            else -> "string"
                        })
                        put("value", if (value is Set<*>) JSONArray(value.toList()) else value.toString())
                    })
                }
            }
            stores.put(values)
        }
        file.baseFile.parentFile?.mkdirs()
        val output = file.startWrite()
        try {
            output.write(stores.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
            check(file.baseFile.isFile) { "Could not persist the import recovery journal" }
            active += file.baseFile.absolutePath
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    fun complete() = synchronized(profileLock) {
        // Empty synchronous commits wait for preceding apply() writes to reach disk.
        names.forEach { check(app.getSharedPreferences(it, 0).edit().commit()) }
        file.delete()
        check(!journalExists()) { "Could not remove the committed import journal" }
        active -= file.baseFile.absolutePath
    }

    fun abandonForRecovery() = synchronized(profileLock) { active.remove(file.baseFile.absolutePath); Unit }

    /** Reads may survive a recovery failure, but no caller may write over a pending rollback. */
    fun ensureWritable() = synchronized(profileLock) {
        if (file.baseFile.absolutePath in active) return
        if (journalExists() || quarantinePending()) recoverIfNeeded()
    }

    fun recoverIfNeeded(): Boolean = synchronized(profileLock) {
        if (file.baseFile.absolutePath in active) return false
        if (quarantinePending()) {
            finishQuarantineOrBlock(null)
            return false
        }
        if (!journalExists()) return false
        try {
            restoreJournal()
            true
        } catch (error: Exception) {
            // Once normal edits resume, this snapshot must never be automatically replayed over
            // them. Preserve the bytes for recovery, but retire the automatic rollback durably.
            finishQuarantineOrBlock(error)
            android.util.Log.e("AccountImportJournal", "Import recovery snapshot quarantined", error)
            false
        }
    }

    private fun restoreJournal() {
        val stores = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        check(stores.length() == names.size) { "Invalid import recovery journal" }
        // Decode everything before touching a store.
        val decoded = names.indices.map { index ->
            val values = stores.getJSONObject(index)
            values.keys().asSequence().associateWith { key ->
                val entry = values.getJSONObject(key)
                when (entry.getString("type")) {
                    "set" -> entry.getJSONArray("value").let { array ->
                        (0 until array.length()).mapTo(mutableSetOf()) { array.getString(it) }
                    }
                    "boolean" -> entry.getString("value").toBooleanStrict()
                    "float" -> entry.getString("value").toFloat()
                    "long" -> entry.getString("value").toLong()
                    "int" -> entry.getString("value").toInt()
                    "string" -> entry.getString("value")
                    else -> error("Invalid import recovery value")
                }
            }
        }
        names.forEachIndexed { index, name ->
            val preferences = app.getSharedPreferences(name, 0)
            val editor = preferences.edit()
            val progressRevision = if (index == 1) preferences.getLong("__progress_revision", 0L) + 1L else 0L
            if (index == 2) selectedKeys.forEach(editor::remove) else editor.clear()
            decoded[index].forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putInt(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    is String -> editor.putString(key, value)
                }
            }
            if (index == 1) editor.putLong("__progress_revision", progressRevision)
            check(editor.commit()) { "Could not recover interrupted import" }
        }
        file.delete()
        check(!journalExists()) { "Could not remove the recovered import journal" }
    }

    private fun journalExists(): Boolean =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private fun quarantinePending(): Boolean =
        quarantineMarker.baseFile.exists() || File(quarantineMarker.baseFile.path + ".bak").exists()

    private fun finishQuarantineOrBlock(recoveryError: Exception?) {
        try {
            val parent = checkNotNull(file.baseFile.parentFile)
            val root = File(parent, "recovery-failed")
            if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
                throw IOException("Could not preserve the failed import recovery snapshot")
            }
            val folderName = if (quarantinePending()) {
                quarantineMarker.openRead().bufferedReader().use { it.readText() }
                    .also { check(it.matches(Regex("[a-zA-Z0-9-]+"))) { "Invalid recovery quarantine marker" } }
            } else {
                "$profileKey-${System.currentTimeMillis()}-${UUID.randomUUID()}".also { name ->
                    val output = quarantineMarker.startWrite()
                    try {
                        output.write(name.toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                        quarantineMarker.finishWrite(output)
                        check(quarantineMarker.baseFile.isFile) { "Could not persist recovery quarantine marker" }
                        syncDirectory(parent)
                    } catch (error: Throwable) {
                        quarantineMarker.failWrite(output)
                        throw error
                    }
                }
            }
            val target = File(root, folderName)
            if (!target.isDirectory && !target.mkdirs() && !target.isDirectory) {
                throw IOException("Could not create the recovery quarantine folder")
            }
            syncDirectory(root)
            listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(file.baseFile.path + ".new"))
                .forEach { source ->
                    if (source.exists()) {
                        val destination = File(target, source.name)
                        if (destination.exists() || !source.renameTo(destination)) {
                            throw IOException("Could not retire the failed import recovery snapshot")
                        }
                    }
                }
            syncDirectory(target)
            syncDirectory(parent)
            check(!journalExists()) { "Import recovery snapshot is still active" }
            quarantineMarker.delete()
            check(!quarantinePending()) { "Could not complete recovery quarantine" }
            syncDirectory(parent)
        } catch (error: Exception) {
            throw AccountImportRecoveryBlockedException(recoveryError ?: error).also {
                if (recoveryError != null && recoveryError !== error) it.addSuppressed(error)
            }
        }
    }

    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    private companion object { val active = ConcurrentHashMap.newKeySet<String>() }
}

internal class AccountImportRecoveryBlockedException(cause: Throwable) :
    IOException("Library edits are blocked until the interrupted import recovery can be preserved", cause)

internal fun ensureAccountImportWritesAllowed(context: Context, profileId: String) =
    AccountImportJournal(context, profileId).ensureWritable()
