package com.futo.platformplayer.backend

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class YoutubeReleasesPage(val available: Boolean, val page: GrayjayChannelPage)
private const val MAX_RELEASE_RESPONSE_BYTES = 8L * 1024 * 1024
private const val MAX_RELEASE_JSON_DEPTH = 100

/** Native reader for the Releases tab not exposed by the JavaScript channel API. */
internal class YoutubeChannelReleasesBackend(client: OkHttpClient) {
    private val http = client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    private data class Cached(val page: YoutubeReleasesPage, val expires: Long)
    private data class Cursor(
        val token: String,
        val version: String,
        val author: String,
        val visitedTokens: Set<String>,
        val mutex: Mutex = Mutex(),
        var loaded: GrayjayChannelPage? = null,
    )
    private data class ParsedPage(val playlists: List<GrayjaySearchPlaylist>, val token: String?)
    private val epoch = PluginClientEpoch()
    private val pages = BoundedSessionCache<String, Cached>(64)
    private val cursors = BoundedSessionCache<String, Cursor>(128)
    fun clear() = epoch.invalidate { pages.clear(); cursors.clear() }

    suspend fun firstPage(channelUrl: String): YoutubeReleasesPage {
        val url = youtubeReleasesUrl(channelUrl)
        val generation = epoch.snapshot()
        epoch.current(generation) {
            pages[url]?.takeIf {
                it.expires > System.currentTimeMillis() &&
                    (it.page.page.continuationId == null || cursors[it.page.page.continuationId] != null)
            }
        }?.let { return it.page }
        val html = request(Request.Builder().url(url).get())
        val data = parseYoutubeInitialData(html)
        val tab = youtubeJsonNodes(data, "tabRenderer").firstOrNull {
            it.obj("endpoint").obj("commandMetadata").obj("webCommandMetadata").str("url").substringBefore('?').endsWith("/releases")
        }
        val version = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1).orEmpty()
        val author = youtubeJsonNodes(data, "channelMetadataRenderer").firstOrNull()?.str("title").orEmpty()
        val parsed = tab?.let { parsePage(it.obj("content")) }
        currentCoroutineContext().ensureActive()
        return epoch.current(generation) {
            val result = if (parsed == null) YoutubeReleasesPage(false, GrayjayChannelPage())
            else YoutubeReleasesPage(true, commitPage(parsed, version, author, emptySet()))
            pages[url] = Cached(result, System.currentTimeMillis() + 5 * 60_000L)
            result
        }
    }

    suspend fun loadMore(id: String): GrayjayChannelPage {
        val generation = epoch.snapshot()
        val cursor = epoch.current(generation) { cursors[id] }
            ?: error("Release page expired. Refresh the channel.")
        return cursor.mutex.withLock {
            epoch.current(generation) {
                if (cursors[id] !== cursor) throw SourceSessionChangedException()
                cursor.loaded
            }?.let { return@withLock it }
            val payload = JsonObject().apply {
                add("context", JsonObject().apply { add("client", JsonObject().apply {
                    addProperty("clientName", "WEB"); addProperty("clientVersion", cursor.version)
                    addProperty("hl", "en"); addProperty("gl", "US")
                }) })
                addProperty("continuation", cursor.token)
            }
            val json = request(Request.Builder().url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .post(payload.toString().toRequestBody("application/json".toMediaType())))
            val parsed = parsePage(parseBoundedYoutubeJson(json))
            currentCoroutineContext().ensureActive()
            epoch.current(generation) {
                if (cursors[id] !== cursor) throw SourceSessionChangedException()
                commitPage(parsed, cursor.version, cursor.author, cursor.visitedTokens).also { cursor.loaded = it }
            }
        }
    }

    private suspend fun request(builder: Request.Builder): String {
        currentCoroutineContext().ensureActive()
        val result = http.newCall(builder.header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9").build()).awaitBoundedText(MAX_RELEASE_RESPONSE_BYTES)
        currentCoroutineContext().ensureActive()
        return result
    }

    private fun parsePage(data: JsonElement): ParsedPage = ParsedPage(
        playlists = parseYoutubeReleaseItems(data, ""),
        token = youtubeJsonNodes(data, "continuationCommand").firstOrNull()?.str("token")?.takeIf(String::isNotBlank),
    )

    private fun commitPage(parsed: ParsedPage, version: String, author: String, visited: Set<String>): GrayjayChannelPage {
        val token = parsed.token?.takeIf { version.isNotBlank() && tokenDigest(it) !in visited }
        val id = token?.let {
            "yr:${UUID.randomUUID()}".also { key ->
                cursors[key] = Cursor(it, version, author, (visited + tokenDigest(it)).toList().takeLast(128).toSet())
            }
        }
        return GrayjayChannelPage(playlists = parsed.playlists.map {
            if (it.author.isBlank()) it.copy(author = author) else it
        }, continuationId = id, hasMore = id != null)
    }
}

private fun tokenDigest(token: String): String = MessageDigest.getInstance("SHA-256")
    .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun youtubeReleasesUrl(channelUrl: String): String {
    val uri = URI(channelUrl)
    require(uri.scheme.equals("https", true) && uri.host?.lowercase(Locale.ROOT) in setOf("www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com"))
    val parts = uri.rawPath.orEmpty().trim('/').split('/')
    require(parts.firstOrNull()?.startsWith('@') == true || (parts.firstOrNull() in setOf("channel", "c", "user") && parts.size >= 2))
    val path = if (parts[0].startsWith('@')) parts[0] else parts.take(2).joinToString("/")
    return "https://www.youtube.com/$path/releases?hl=en"
}

internal fun parseYoutubeInitialData(html: String): JsonObject {
    val start = Regex("(?:var\\s+)?ytInitialData\\s*=\\s*|window\\[\"ytInitialData\"\\]\\s*=\\s*")
        .find(html)?.range?.last?.plus(1) ?: error("YouTube channel data missing.")
    val begin = (start until html.length).firstOrNull { !html[it].isWhitespace() }
        ?: error("Incomplete YouTube channel data.")
    require(html[begin] == '{') { "Invalid YouTube channel data." }
    var depth = 0; var quoted = false; var escaped = false
    for (index in begin until html.length) {
        val ch = html[index]
        if (quoted) { if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false }
        else when (ch) {
            '"' -> quoted = true
            '{' -> depth++
            '}' -> if (--depth == 0) return parseBoundedYoutubeJson(html.substring(begin, index + 1)).asJsonObject
        }
    }
    error("Incomplete YouTube channel data.")
}

internal fun parseBoundedYoutubeJson(json: String): JsonElement {
    require(json.length <= MAX_RELEASE_RESPONSE_BYTES) { "YouTube channel data exceeds the size limit." }
    var depth = 0; var quoted = false; var escaped = false
    var values = 0
    var primitive = false
    for (ch in json) {
        if (quoted) {
            if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
        } else when (ch) {
            '"' -> { values++; quoted = true; primitive = false }
            '{', '[' -> { values++; primitive = false; require(++depth <= MAX_RELEASE_JSON_DEPTH) { "YouTube channel data is nested too deeply." } }
            '}', ']' -> { depth--; primitive = false }
            ',', ':' -> primitive = false
            else -> if (!ch.isWhitespace() && !primitive) { primitive = true; values++ }
        }
        require(values <= 100_000) { "YouTube channel data contains too many values." }
    }
    return JsonParser.parseString(json)
}

internal fun youtubeJsonNodes(root: JsonElement, key: String): List<JsonObject> {
    val found = mutableListOf<JsonObject>()
    val pending = ArrayDeque<Pair<JsonElement, Int>>(); pending.add(root to 0)
    var visited = 0
    while (pending.isNotEmpty() && visited++ < 100_000) {
        val (item, depth) = pending.removeLast()
        if (depth > 100) continue
        if (item.isJsonObject) {
            val obj = item.asJsonObject
            obj.get(key)?.takeIf { it.isJsonObject }?.let { found.add(it.asJsonObject) }
            obj.entrySet().toList().asReversed().forEach { pending.add(it.value to depth + 1) }
        } else if (item.isJsonArray) item.asJsonArray.toList().asReversed().forEach { pending.add(it to depth + 1) }
    }
    return found
}

internal fun parseYoutubeReleaseItems(data: JsonElement, author: String): List<GrayjaySearchPlaylist> {
    val result = mutableListOf<GrayjaySearchPlaylist>()
    for (key in listOf("gridPlaylistRenderer", "playlistRenderer", "lockupViewModel")) {
        for (item in youtubeJsonNodes(data, key)) {
            val id = item.str("playlistId").ifBlank { item.str("contentId") }
            if (id.isBlank() || (key == "lockupViewModel" && !item.str("contentType").contains("PLAYLIST"))) continue
            val title = item.obj("title").text().ifBlank { item.obj("metadata").obj("lockupMetadataViewModel").obj("title").str("content") }
            if (title.isBlank()) continue
            val thumb = youtubeImageUrl(item)
            result.add(GrayjaySearchPlaylist(id, "https://www.youtube.com/playlist?list=$id", "youtube", "youtube", title,
                item.obj("shortBylineText").text().ifBlank { author }, thumb, -1))
        }
    }
    return result.distinctBy(GrayjaySearchPlaylist::id)
}

private fun youtubeImageUrl(item: JsonElement, depth: Int = 0): String? {
    if (depth > 100) return null
    if (item.isJsonObject) {
        val obj = item.asJsonObject
        val url = obj.str("url")
        if (url.startsWith("https://") && (obj.has("width") || obj.has("height"))) return url
        obj.entrySet().forEach { youtubeImageUrl(it.value, depth + 1)?.let { url -> return url } }
    } else if (item.isJsonArray) item.asJsonArray.forEach { youtubeImageUrl(it, depth + 1)?.let { url -> return url } }
    return null
}
private fun JsonObject.obj(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
private fun JsonObject.str(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
private fun JsonObject.text(): String = str("simpleText").ifBlank {
    get("runs")?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonObject }
        ?.joinToString("") { it.asJsonObject.str("text") }.orEmpty()
}
