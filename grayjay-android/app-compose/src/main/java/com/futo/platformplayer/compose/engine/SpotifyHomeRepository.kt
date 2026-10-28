package com.futo.platformplayer.compose.engine

import android.content.Context
import android.util.AtomicFile
import com.futo.platformplayer.compose.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Public, localized catalogue sections. No account cookies, playback lookup or JS runtime. */
internal class SpotifyHomeRepository(private val context: Context) {
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val cacheDir = File(context.cacheDir, "music-home")
    fun invalidate() { cacheDir.listFiles()?.filter { it.isFile }?.forEach(File::delete) }

    suspend fun load(forceRefresh: Boolean): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val locale = context.resources.configuration.locales[0].toLanguageTag()
            val cache = AtomicFile(File(cacheDir, "${locale.replace(Regex("[^A-Za-z0-9-]"), "")}.json"))
            val old = runCatching { cache.openRead().bufferedReader().use { it.readText() } }.getOrNull()
            val age = System.currentTimeMillis() - cache.baseFile.lastModified()
            if (!forceRefresh && age in 0..1_200_000 && old != null) {
                val cached = runCatching { parseSpotifyHomeState(old) }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) return@withLock cached
            }
            try {
                val request = Request.Builder().url("https://open.spotify.com/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                    .header("Accept-Language", "$locale,en;q=0.5").build()
                val state = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Spotify HTTP ${response.code}" }
                    val body = checkNotNull(response.body)
                    val source = body.source()
                    source.request(4L * 1024 * 1024 + 1)
                    require(source.buffer.size <= 4L * 1024 * 1024) { "Spotify page too large" }
                    decodeSpotifyHomeState(source.buffer.readUtf8())
                }
                val sections = parseSpotifyHomeState(state)
                check(sections.isNotEmpty()) { "Spotify home sections unavailable" }
                cacheDir.mkdirs()
                runCatching {
                    val stream = cache.startWrite()
                    try { stream.write(state.toByteArray()); cache.finishWrite(stream) }
                    catch (error: Exception) { cache.failWrite(stream); throw error }
                }
                sections
            } catch (error: Exception) {
                if (!forceRefresh && old != null && age in 0..86_400_000) {
                    runCatching { parseSpotifyHomeState(old) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: throw error
                } else throw error
            }
        }
    }
}

internal fun decodeSpotifyHomeState(html: String): String {
    val encoded = Regex("<script\\b[^>]*\\bid=[\"']initialState[\"'][^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1)?.trim() ?: error("Spotify home data unavailable")
    require(encoded.length <= 4 * 1024 * 1024)
    return Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8)
}

internal fun parseSpotifyHomeState(state: String): List<MusicHomeSection> {
    val sections = JSONObject(state).optJSONObject("home")?.optJSONObject("data")?.optJSONArray("sections") ?: return emptyList()
    return (0 until sections.length().coerceAtMost(40)).mapNotNull { index ->
        val section = sections.optJSONObject(index) ?: return@mapNotNull null
        val items = section.optJSONArray("items") ?: return@mapNotNull null
        val entries = (0 until items.length().coerceAtMost(100)).mapNotNull item@{ i ->
            val item = items.optJSONObject(i) ?: return@item null
            val uri = item.optString("uri")
            val match = Regex("spotify:(track|artist|album|playlist):([A-Za-z0-9]{22})").matchEntire(uri) ?: return@item null
            val type = match.groupValues[1]
            val url = "https://open.spotify.com/$type/${match.groupValues[2]}"
            val title = item.optString("title").take(400).takeIf(String::isNotBlank) ?: return@item null
            val image = item.optString("imageUrl").takeIf { it.startsWith("https://") }.orEmpty()
            MusicHomeItem(url, title, type, image)
        }.distinctBy(MusicHomeItem::url)
        if (entries.isEmpty()) null else MusicHomeSection(section.optString("uri").ifBlank { "section-$index" }, section.optString("title"), entries)
    }.distinctBy(MusicHomeSection::id)
}
