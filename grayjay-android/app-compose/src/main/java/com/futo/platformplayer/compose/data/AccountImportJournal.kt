package com.futo.platformplayer.compose.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A write-ahead copy lets the next process roll back a kill between preference-file writes. */
internal class AccountImportJournal(context: Context, profileId: String) {
    private val app = context.applicationContext
    private val suffix = if (profileId == "main") "" else "_$profileId"
    private val profileKey = MessageDigest.getInstance("SHA-256")
        .digest(profileId.toByteArray()).joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(app.filesDir, "account-import/$profileKey.json"))
    private val names = listOf(
        "grayjay_compose_library_v2$suffix",
        "grayjay_compose_watch_progress_v1$suffix",
        "grayjay_compose_preferences$suffix",
    )
    private val selectedKeys = setOf("followed_creator_ids", "following_initialized", "imported_channels")

    fun begin() = synchronized(active) {
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
            active += file.baseFile.absolutePath
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    fun complete() = synchronized(active) {
        // Empty synchronous commits wait for preceding apply() writes to reach disk.
        names.forEach { check(app.getSharedPreferences(it, 0).edit().commit()) }
        file.delete()
        active -= file.baseFile.absolutePath
    }

    fun abandonForRecovery() = synchronized(active) { active.remove(file.baseFile.absolutePath); Unit }

    fun recoverIfNeeded(): Boolean = synchronized(active) {
        if (file.baseFile.absolutePath in active ||
            (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())) return false
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
            val editor = app.getSharedPreferences(name, 0).edit()
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
            check(editor.commit()) { "Could not recover interrupted import" }
        }
        file.delete()
        true
    }

    private companion object { val active = mutableSetOf<String>() }
}
