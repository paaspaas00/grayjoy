package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class LegacyCachedVideo(
    val video: VideoUiModel,
    val durationSeconds: Long,
)

internal data class LegacyHistoryEntry(
    val url: String,
    val watchedAtEpochSeconds: Long,
    val positionSeconds: Long,
    val title: String,
)

internal data class LegacyPlaylistEntry(
    val id: String,
    val title: String,
    val videoUrls: List<String>,
)

internal data class LegacyGrayjayBackup(
    val pluginConfigUrls: Map<String, String>,
    val pluginSettings: Map<String, Map<String, String?>>,
    val cachedVideos: Map<String, LegacyCachedVideo>,
    val cachedChannels: Map<String, ChannelUiModel>,
    val subscriptionUrls: List<String>,
    val watchLaterUrls: List<String>,
    val playlists: List<LegacyPlaylistEntry>,
    val history: List<LegacyHistoryEntry>,
    val hasSettings: Boolean,
) {
    fun referencedVideo(
        url: String,
        titleHint: String = "",
    ): LegacyCachedVideo = cachedVideos[url] ?: LegacyCachedVideo(
        video = VideoUiModel(
            id = url,
            title = titleHint.ifBlank { url },
            creator = "Unknown creator",
            metadata = "Imported from Grayjay",
            duration = "",
            sourceId = inferSourceId(url),
            contentUrl = url,
            shareUrl = url,
        ),
        durationSeconds = 0L,
    )
}

internal open class LegacyBackupException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

internal class LegacyBackupPasswordRequiredException :
    LegacyBackupException("This Grayjay automatic backup is encrypted and needs its backup password.")

internal class LegacyBackupInvalidPasswordException(cause: Throwable? = null) :
    LegacyBackupException("The backup password is incorrect.", cause)

internal object LegacyGrayjayBackupParser {
    private val autoMagic = byteArrayOf(0x11, 0x22, 0x33, 0x44)
    private val newAutoMarker = byteArrayOf('G'.code.toByte(), 'J'.code.toByte())

    fun parse(data: ByteArray, password: String? = null): LegacyGrayjayBackup {
        if (data.isEmpty()) throw LegacyBackupException("The selected backup is empty.")
        if (data.size > MAX_BACKUP_BYTES) {
            throw LegacyBackupException("The selected backup is larger than 128 MB.")
        }
        return parseZip(normalizeToZip(data, password))
    }

    private fun normalizeToZip(data: ByteArray, password: String?): ByteArray = when {
        data.isZip() -> data
        data.hasPrefix(autoMagic) && data.size >= 8 &&
            data[4] == newAutoMarker[0] && data[5] == newAutoMarker[1] -> {
            val version = data[6].toInt() and 0xFF
            val flags = data[7].toInt() and 0xFF
            if (version != 1) throw LegacyBackupException(
                "Unsupported Grayjay automatic backup version: $version.",
            )
            if (flags and 0x01 != 0) throw LegacyBackupException(
                "This newer encrypted Grayjay backup format is not supported by the legacy app either.",
            )
            data.copyOfRange(8, data.size).requireZip()
        }
        data.hasPrefix(autoMagic) -> {
            if (data.size < 6) throw LegacyBackupException("The automatic backup header is incomplete.")
            val suppliedPassword = password?.takeIf(String::isNotBlank)
                ?: throw LegacyBackupPasswordRequiredException()
            if (data[4].toInt() != 1) {
                throw LegacyBackupException("Unsupported legacy encryption version: ${data[4].toInt()}.")
            }
            decryptV1(data.copyOfRange(5, data.size), suppliedPassword).requireZip()
        }
        else -> {
            val suppliedPassword = password?.takeIf(String::isNotBlank)
                ?: throw LegacyBackupPasswordRequiredException()
            decryptV0(data, suppliedPassword).requireZip()
        }
    }

    private fun parseZip(zipBytes: ByteArray): LegacyGrayjayBackup {
        val entries = linkedMapOf<String, String>()
        var totalUncompressed = 0L
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entryCount = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                entryCount += 1
                if (entryCount > MAX_ENTRIES) {
                    throw LegacyBackupException("The backup contains too many files.")
                }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entryBytes = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytes += read
                    totalUncompressed += read
                    if (entryBytes > MAX_ENTRY_BYTES || totalUncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw LegacyBackupException("The backup expands beyond the safe import limit.")
                    }
                    output.write(buffer, 0, read)
                }
                entries[entry.name] = output.toString(StandardCharsets.UTF_8.name())
            }
        }
        if (entries.isEmpty() || entries.keys.none { it == "exportInfo" || it.startsWith("stores/") }) {
            throw LegacyBackupException("This is not a Grayjay export database.")
        }

        val stores = entries
            .filterKeys { it.startsWith("stores/") && it.length > "stores/".length }
            // ManagedDBStore names in real Grayjay exports are not consistently cased
            // (for example `Subscriptions`, `Watch_later`, and `Playlists`). Store
            // lookup is conceptually case-insensitive, so normalize archive names here.
            .mapKeys { it.key.substringAfter("stores/").lowercase() }
            .mapValues { (_, json) -> json.parseStringArray() }
        val plugins = entries["plugins"].parseStringMap()
        val pluginSettings = entries["plugin_settings"].parseNullableStringMapMap()
        val cachedVideos = entries["cache_videos"].parseArrayOrEmpty()
            .mapNotNull(::parseCachedVideo)
            .associateBy { it.video.contentUrl }
        val cachedChannels = entries["cache_channels"].parseArrayOrEmpty()
            .mapNotNull(::parseCachedChannel)
            .associateBy(ChannelUiModel::id)

        return LegacyGrayjayBackup(
            pluginConfigUrls = plugins,
            pluginSettings = pluginSettings,
            cachedVideos = cachedVideos,
            cachedChannels = cachedChannels,
            subscriptionUrls = stores["subscriptions"].orEmpty().distinct(),
            watchLaterUrls = stores["watch_later"].orEmpty().distinct(),
            playlists = stores["playlists"].orEmpty().mapNotNull(::parsePlaylist),
            history = stores["history"].orEmpty().mapNotNull(::parseHistory),
            hasSettings = entries.containsKey("settings"),
        )
    }

    private fun parseCachedVideo(element: JsonElement): LegacyCachedVideo? {
        val json = element.asObjectOrNull() ?: return null
        val url = json.string("url").takeIf(String::isNotBlank) ?: return null
        val author = json.objectValue("author")
        val pluginId = json.objectValue("id")?.string("pluginId")
            .orEmpty()
            .ifBlank { author?.objectValue("id")?.string("pluginId").orEmpty() }
            .ifBlank { inferSourceId(url) }
        val durationSeconds = json.long("duration").coerceAtLeast(0L)
        val viewCount = json.long("viewCount", -1L)
        val authorUrl = author?.string("url").orEmpty()
        val title = json.string("name").ifBlank { url }
        val metadata = if (viewCount >= 0L) "${formatCompactCount(viewCount)} views" else ""
        val thumbnail = json.objectValue("thumbnails")
            ?.arrayValue("sources")
            ?.mapNotNull { item ->
                item.asObjectOrNull()?.let { thumb -> thumb.int("quality") to thumb.string("url") }
            }
            ?.filter { it.second.isNotBlank() }
            ?.maxByOrNull(Pair<Int, String>::first)
            ?.second
            .orEmpty()
        return LegacyCachedVideo(
            video = VideoUiModel(
                id = url,
                title = title,
                creator = author?.string("name").orEmpty().ifBlank { "Unknown creator" },
                metadata = metadata,
                duration = formatDuration(durationSeconds),
                channelId = authorUrl,
                sourceId = pluginId,
                contentUrl = url,
                thumbnailUrl = thumbnail,
                shareUrl = json.string("shareUrl").ifBlank { url },
                authorUrl = authorUrl,
                authorThumbnailUrl = author?.string("thumbnail").orEmpty(),
                authorSubscriberCount = author?.nullableLong("subscribers"),
            ),
            durationSeconds = durationSeconds,
        )
    }

    private fun parseCachedChannel(element: JsonElement): ChannelUiModel? {
        val json = element.asObjectOrNull() ?: return null
        val url = json.string("url").takeIf(String::isNotBlank) ?: return null
        val pluginId = json.objectValue("id")?.string("pluginId")
            .orEmpty()
            .ifBlank { inferSourceId(url) }
        val subscribers = json.long("subscribers", -1L)
        return ChannelUiModel(
            id = url,
            name = json.string("name").ifBlank { "Imported creator" },
            sourceId = pluginId,
            source = "",
            unreadCount = 0,
            followerCount = if (subscribers >= 0L) "${formatCompactCount(subscribers)} followers" else "Creator",
            description = json.string("description"),
            thumbnailUrl = json.string("thumbnail"),
        )
    }

    private fun parseHistory(value: String): LegacyHistoryEntry? {
        val parts = value.split("|||", limit = 4)
        if (parts.size < 4 || parts[0].isBlank()) return null
        return LegacyHistoryEntry(
            url = parts[0],
            watchedAtEpochSeconds = parts[1].toLongOrNull() ?: 0L,
            positionSeconds = parts[2].toLongOrNull() ?: 0L,
            title = parts[3],
        )
    }

    private fun parsePlaylist(value: String): LegacyPlaylistEntry? {
        val lines = value.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        if (lines.isEmpty()) return null
        val header = lines.first()
        val separator = header.indexOf(":::")
        val title = if (separator >= 0) header.substring(0, separator) else header
        val id = if (separator >= 0) header.substring(separator + 3) else ""
        return LegacyPlaylistEntry(
            id = id.ifBlank { "legacy-${title.hashCode().toUInt()}" },
            title = title.ifBlank { "Imported playlist" },
            videoUrls = lines.drop(1).distinct(),
        )
    }

    private fun decryptV1(data: ByteArray, password: String): ByteArray = try {
        if (password.toByteArray().size !in 4..32 || data.size <= 28) {
            throw LegacyBackupInvalidPasswordException()
        }
        val salt = data.copyOfRange(0, 16)
        val iv = data.copyOfRange(16, 28)
        val encrypted = data.copyOfRange(28, data.size)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 131_072, 256)
        val key = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            doFinal(encrypted)
        }
    } catch (error: LegacyBackupInvalidPasswordException) {
        throw error
    } catch (error: AEADBadTagException) {
        throw LegacyBackupInvalidPasswordException(error)
    } catch (error: Throwable) {
        throw LegacyBackupInvalidPasswordException(error)
    }

    private fun decryptV0(data: ByteArray, password: String): ByteArray = try {
        if (password.toByteArray().size !in 4..32) throw LegacyBackupInvalidPasswordException()
        val paddedPassword = password.padStart(32, '9')
        val key = SecretKeySpec(paddedPassword.toByteArray(), "AES")
        val fixedIv = byteArrayOf(12, 43, 127, 2, 99, 22, 6, 78, 24, 53, 8, 101)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, fixedIv))
            doFinal(data)
        }
    } catch (error: LegacyBackupInvalidPasswordException) {
        throw error
    } catch (error: Throwable) {
        throw LegacyBackupInvalidPasswordException(error)
    }

    private fun ByteArray.requireZip(): ByteArray = apply {
        if (!isZip()) throw LegacyBackupException("The decrypted data is not a valid Grayjay export.")
    }

    private fun ByteArray.isZip(): Boolean = size >= 2 && this[0] == 0x50.toByte() && this[1] == 0x4B.toByte()
    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val MAX_BACKUP_BYTES = 128 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 96L * 1024 * 1024
    private const val MAX_ENTRIES = 256
}

internal fun LegacyGrayjayBackup.buildImportLibrary(
    includeWatchLater: Boolean,
    includePlaylists: Boolean,
    includeHistory: Boolean,
    importedDescription: String = "Imported from Grayjay",
): Pair<List<VideoUiModel>, List<PlaylistUiModel>> {
    val videos = linkedMapOf<String, VideoUiModel>()

    fun merge(video: VideoUiModel) {
        val existing = videos[video.id]
        videos[video.id] = if (existing == null) video else existing.copy(
            isWatchLater = existing.isWatchLater || video.isWatchLater,
            watchProgress = if (video.lastWatchedAt >= existing.lastWatchedAt) video.watchProgress else existing.watchProgress,
            lastWatchedAt = maxOf(existing.lastWatchedAt, video.lastWatchedAt),
        )
    }

    if (includeWatchLater) watchLaterUrls.forEach { url ->
        merge(referencedVideo(url).video.copy(isWatchLater = true))
    }
    val importedPlaylists = if (includePlaylists) playlists.map { legacy ->
        legacy.videoUrls.forEach { url -> merge(referencedVideo(url).video) }
        PlaylistUiModel(
            id = legacy.id,
            title = legacy.title,
            description = importedDescription,
            videoIds = legacy.videoUrls,
        )
    } else {
        emptyList()
    }
    if (includeHistory) history.forEach { entry ->
        val cached = referencedVideo(entry.url, entry.title)
        val progress = if (cached.durationSeconds > 0L) {
            entry.positionSeconds.toFloat().div(cached.durationSeconds).coerceIn(0f, 1f)
        } else if (entry.positionSeconds > 0L) {
            0.01f
        } else {
            0f
        }
        merge(
            cached.video.copy(
                watchProgress = progress,
                lastWatchedAt = entry.watchedAtEpochSeconds.coerceAtLeast(0L) * 1_000L,
            ),
        )
    }
    return videos.values.toList() to importedPlaylists
}

private fun inferSourceId(url: String): String = when {
    "youtube.com" in url || "youtu.be" in url -> "youtube"
    "odysee.com" in url -> "odysee"
    "rumble.com" in url -> "rumble"
    "twitch.tv" in url -> "twitch"
    "soundcloud.com" in url -> "soundcloud"
    "bilibili.com" in url -> "bilibili"
    "dailymotion.com" in url || "dai.ly" in url -> "dailymotion"
    "bitchute.com" in url -> "bitchute"
    else -> "youtube"
}

private fun String?.parseArrayOrEmpty(): JsonArray = runCatching {
    this?.let(JsonParser::parseString)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
}.getOrNull() ?: JsonArray()

private fun String?.parseObjectOrNull(): JsonObject? = runCatching {
    this?.let(JsonParser::parseString)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
}.getOrNull()

private fun String?.parseStringArray(): List<String> = parseArrayOrEmpty().mapNotNull { value ->
    runCatching { value.asString }.getOrNull()?.takeIf(String::isNotBlank)
}

private fun String?.parseStringMap(): Map<String, String> = parseObjectOrNull()
    ?.entrySet()
    ?.mapNotNull { (key, value) ->
        runCatching { value.asString }.getOrNull()?.takeIf(String::isNotBlank)?.let { key to it }
    }
    ?.toMap()
    .orEmpty()

private fun String?.parseNullableStringMapMap(): Map<String, Map<String, String?>> =
    parseObjectOrNull()?.entrySet()?.mapNotNull { (pluginId, settingsElement) ->
        val settings = settingsElement.asObjectOrNull() ?: return@mapNotNull null
        pluginId to settings.entrySet().associate { (key, value) ->
            key to if (value.isJsonNull) null else runCatching { value.asString }.getOrNull()
        }
    }?.toMap().orEmpty()

private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf(JsonElement::isJsonObject)?.asJsonObject
private fun JsonObject.string(key: String): String = get(key)?.takeUnless(JsonElement::isJsonNull)?.let {
    runCatching { it.asString }.getOrDefault("")
}.orEmpty()
private fun JsonObject.long(key: String, default: Long = 0L): Long = get(key)?.let {
    runCatching { it.asLong }.getOrDefault(default)
} ?: default
private fun JsonObject.int(key: String, default: Int = 0): Int = get(key)?.let {
    runCatching { it.asInt }.getOrDefault(default)
} ?: default
private fun JsonObject.nullableLong(key: String): Long? = get(key)?.takeUnless(JsonElement::isJsonNull)?.let {
    runCatching { it.asLong }.getOrNull()
}
private fun JsonObject.objectValue(key: String): JsonObject? = get(key)?.asObjectOrNull()
private fun JsonObject.arrayValue(key: String): JsonArray? = get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return ""
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).trimEnd('0').trimEnd('.')
    else -> value.toString()
}
