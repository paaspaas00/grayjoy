package com.futo.platformplayer.backend

import android.content.Context
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Reads only the active account's display identity; no JavaScript runtime or shared browser cookies. */
class YoutubeAccountIdentity(context: Context) {
    private val app = context.applicationContext
    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun refresh(profileId: String, pluginId: String): String? = withContext(Dispatchers.IO) {
        val auth = GrayjayPluginAuthStore.load(app, profileId, pluginId) ?: return@withContext null
        val cached = GrayjayPluginAuthStore.accountName(app, profileId, pluginId)
        val age = System.currentTimeMillis() - GrayjayPluginAuthStore.accountNameCheckedAt(app, profileId, pluginId)
        if (cached != null && age in 0 until 6 * 60 * 60_000L) return@withContext cached
        val name = withTimeoutOrNull(12_000L) { fetch(auth) } ?: return@withContext cached
        currentCoroutineContext().ensureActive()
        if (GrayjayPluginAuthStore.saveAccountName(app, profileId, pluginId, auth, name)) name else null
    }

    private suspend fun fetch(auth: SourceAuth): String? {
        val session = JSHttpClient(jsClient = null, auth = auth)
        suspend fun request(builder: Request.Builder): String {
            currentCoroutineContext().ensureActive()
            val request = session.beforeRequest(builder
                .header("User-Agent", DESKTOP_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Origin", "https://www.youtube.com")
                .header("X-Origin", "https://www.youtube.com").build())
            return http.newCall(request).awaitBoundedText(8L * 1024 * 1024)
        }
        val html = request(Request.Builder().url("https://www.youtube.com/?hl=en"))
        val initial = runCatching { parseYoutubeInitialData(html) }.getOrNull()
        initial?.let(::youtubeActiveAccountName)?.let { return it }
        val config = youtubeAccountConfig(html) ?: return null
        if (config.get("LOGGED_IN")?.let { it.isJsonPrimitive && it.asString == "false" } == true) return null
        val context = config.get("INNERTUBE_CONTEXT")?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy()
            ?: return null
        config.text("DELEGATED_SESSION_ID")?.let { page ->
            val user = context.getAsJsonObject("user") ?: JsonObject().also { context.add("user", it) }
            user.addProperty("onBehalfOfUser", page)
        }
        for (endpoint in listOf("account_menu", "accounts_list")) {
            val body = JsonObject().apply {
                add("context", context)
                if (endpoint == "accounts_list") {
                    addProperty("requestType", "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER")
                    addProperty("callCircumstance", "SWITCHING_USERS_FULL")
                }
            }
            val builder = Request.Builder().url("https://www.youtube.com/youtubei/v1/account/$endpoint?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            config.text("SESSION_INDEX")?.let { builder.header("X-Goog-AuthUser", it) }
            config.text("DELEGATED_SESSION_ID")?.let { builder.header("X-Goog-PageId", it) }
            val name = try { youtubeActiveAccountName(parseBoundedYoutubeJson(request(builder))) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            if (name != null) return name
        }
        return null
    }

    private companion object {
        const val DESKTOP_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}

/** Never pick another account listed in the account switcher, or a channel appearing in page content. */
internal fun youtubeActiveAccountName(root: JsonElement): String? {
    for (key in listOf("activeAccountHeaderRenderer", "accountHeaderRenderer")) {
        for (header in youtubeJsonNodes(root, key)) {
            for (field in listOf("accountName", "email", "channelHandle")) {
                youtubeAccountText(header.get(field))?.let { return it }
            }
        }
    }
    return (youtubeJsonNodes(root, "accountItemRenderer") + youtubeJsonNodes(root, "accountItem"))
        .filter { it.get("isSelected")?.let { flag -> flag.isJsonPrimitive && flag.asString == "true" } == true }
        .firstNotNullOfOrNull { youtubeAccountText(it.get("accountName")) }
}

private fun youtubeAccountText(value: JsonElement?): String? {
    val obj = value?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val text = obj.text("simpleText") ?: obj.get("runs")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.joinToString("") { it.takeIf { run -> run.isJsonObject }?.asJsonObject?.text("text").orEmpty() }
    return text?.filterNot { it.isISOControl() }?.trim()?.take(320)?.takeIf(String::isNotBlank)
}

private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }
    ?.asString?.takeIf { it.isNotBlank() }

internal fun youtubeAccountConfig(html: String): JsonObject? {
    val merged = JsonObject()
    for (marker in Regex("ytcfg\\.set\\s*\\(").findAll(html)) {
        val begin = (marker.range.last + 1 until html.length).firstOrNull { !html[it].isWhitespace() } ?: continue
        if (html[begin] != '{') continue
        var depth = 0; var quoted = false; var escaped = false
        for (index in begin until html.length) {
            val ch = html[index]
            if (quoted) { if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false }
            else when (ch) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) {
                    val config = runCatching { parseBoundedYoutubeJson(html.substring(begin, index + 1)).asJsonObject }.getOrNull()
                    config?.entrySet()?.forEach { merged.add(it.key, it.value) }
                    break
                }
            }
        }
    }
    return merged.takeIf { it.has("INNERTUBE_CONTEXT") }
}
