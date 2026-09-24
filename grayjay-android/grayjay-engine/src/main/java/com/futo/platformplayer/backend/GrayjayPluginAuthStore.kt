package com.futo.platformplayer.backend

import android.content.Context
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import org.json.JSONObject
import java.io.IOException

object GrayjayPluginAuthStore {
    private const val FILE_NAME = "grayjay-js-plugin-auth"

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

    fun clear(context: Context, profileId: String, pluginId: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(profileId, pluginId))
            .apply { if (profileId == "main") remove(pluginId) }
            .apply()
    }

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
