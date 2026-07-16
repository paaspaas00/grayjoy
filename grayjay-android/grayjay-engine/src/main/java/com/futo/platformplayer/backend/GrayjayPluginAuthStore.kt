package com.futo.platformplayer.backend

import android.content.Context
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import org.json.JSONObject

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
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(profileId, pluginId), json.toString())
            .commit()
    }

    fun load(context: Context, profileId: String, pluginId: String): SourceAuth? = runCatching {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val raw = preferences.getString(key(profileId, pluginId), null)
            ?: if (profileId == "main") preferences.getString(pluginId, null) else null
            ?: return null
        val json = JSONObject(raw)
        SourceAuth(
            cookieMap = json.optJSONObject("cookies").toNestedMap(),
            headers = json.optJSONObject("headers").toNestedMap(),
            userAgent = json.optString("userAgent").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    fun clear(context: Context, profileId: String, pluginId: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(profileId, pluginId))
            .apply()
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
