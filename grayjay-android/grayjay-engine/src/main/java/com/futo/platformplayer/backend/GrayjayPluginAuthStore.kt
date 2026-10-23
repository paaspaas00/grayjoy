package com.futo.platformplayer.backend

import android.content.Context
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import org.json.JSONObject
import java.io.IOException

object GrayjayPluginAuthStore {
    private const val FILE_NAME = "grayjay-js-plugin-auth"

    @Synchronized
    fun save(context: Context, profileId: String, pluginId: String, auth: SourceAuth) {
        val cookies = JSONObject().apply {
            auth.cookieMap.orEmpty().forEach { (domain, values) -> put(domain, JSONObject(values as Map<*, *>)) }
        }
        val headers = JSONObject().apply {
            auth.headers.forEach { (domain, values) -> put(domain, JSONObject(values)) }
        }
        val json = JSONObject().apply {
            put("cookies", cookies)
            put("headers", headers)
            put("userAgent", auth.userAgent ?: JSONObject.NULL)
        }
        val saved = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(profileId, pluginId), json.toString())
            .apply { if (profileId == "main") remove(pluginId) }
            .commit()
        if (!saved) throw IOException("Unable to save source authentication")
    }

    @Synchronized
    fun load(context: Context, profileId: String, pluginId: String): SourceAuth? = runCatching {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val raw = preferences.getString(key(profileId, pluginId), null)
            ?: (if (profileId == "main") preferences.getString(pluginId, null) else null)
            ?: return null
        val json = JSONObject(raw)
        SourceAuth(
            cookieMap = json.optJSONObject("cookies").toNestedMap(),
            headers = json.optJSONObject("headers").toNestedMap(),
            userAgent = if (json.isNull("userAgent")) null else
                json.optString("userAgent").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    /** Copy the entire session, including account metadata, without changing either login. */
    @Synchronized
    fun copyIfAbsent(context: Context, fromProfile: String, toProfile: String, pluginId: String): Boolean {
        if (has(context, toProfile, pluginId)) return false
        if (!has(context, fromProfile, pluginId)) return false
        val raw = storedSession(context, fromProfile, pluginId) ?: return false
        if (!context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
                .putString(key(toProfile, pluginId), raw).commit()) {
            throw IOException("Unable to copy source authentication")
        }
        return true
    }

    @Synchronized
    fun accountName(context: Context, profileId: String, pluginId: String): String? = runCatching {
        JSONObject(storedSession(context, profileId, pluginId) ?: return null)
            .optString("accountName").takeIf(String::isNotBlank)
    }.getOrNull()

    @Synchronized
    fun accountNameCheckedAt(context: Context, profileId: String, pluginId: String): Long = runCatching {
        JSONObject(storedSession(context, profileId, pluginId) ?: return 0L).optLong("accountNameCheckedAt")
    }.getOrDefault(0L)

    /** A late network response must never resurrect a signed-out or replaced account. */
    @Synchronized
    fun saveAccountName(context: Context, profileId: String, pluginId: String, expectedAuth: SourceAuth,
        name: String, checkedAt: Long = System.currentTimeMillis()): Boolean {
        if (name.isBlank() || load(context, profileId, pluginId) != expectedAuth) return false
        val json = JSONObject(storedSession(context, profileId, pluginId) ?: return false)
            .put("accountName", name.trim().take(320)).put("accountNameCheckedAt", checkedAt)
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putString(key(profileId, pluginId), json.toString()).commit()
    }

    private fun storedSession(context: Context, profileId: String, pluginId: String): String? {
        val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key(profileId, pluginId), null)
            ?: if (profileId == "main") prefs.getString(pluginId, null) else null
    }

    @Synchronized
    fun clear(context: Context, profileId: String, pluginId: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(profileId, pluginId))
            .apply { if (profileId == "main") remove(pluginId) }
            .apply()
    }

    @Synchronized
    fun clearProfile(context: Context, profileId: String) {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val prefix = "$profileId:"
        preferences.edit().apply {
            preferences.all.keys.filter {
                it.startsWith(prefix) || (profileId == "main" && ':' !in it)
            }.forEach(::remove)
        }.apply()
    }

    fun has(context: Context, profileId: String, pluginId: String): Boolean =
        load(context, profileId, pluginId) != null

    private fun key(profileId: String, pluginId: String) = "$profileId:$pluginId"

    private fun JSONObject?.toNestedMap(): HashMap<String, HashMap<String, String>> = hashMapOf<String, HashMap<String, String>>().apply {
        val root = this@toNestedMap ?: return@apply
        root.keys().forEach { domain ->
            val values = root.optJSONObject(domain) ?: return@forEach
            put(
                domain,
                hashMapOf<String, String>().apply {
                    values.keys().forEach { name -> put(name, values.optString(name)) }
                },
            )
        }
    }
}
