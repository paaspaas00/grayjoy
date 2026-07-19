package com.futo.platformplayer.compose.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

internal data class NewPipeSubscription(
    val serviceId: Int,
    val url: String,
    val name: String,
    val avatarUrl: String = "",
    val subscriberCount: Long? = null,
    val description: String = "",
)

internal data class NewPipeStream(
    val id: Long,
    val serviceId: Int,
    val url: String,
    val title: String,
    val streamType: Int,
    val durationSeconds: Long,
    val uploader: String,
    val uploaderUrl: String,
    val thumbnailUrl: String,
    val viewCount: Long? = null,
)

internal data class NewPipeHistoryEntry(
    val streamId: Long,
    val accessedAtMs: Long,
    val progressMs: Long,
)

internal data class NewPipePlaylistEntry(
    val id: Long,
    val name: String,
    val streamIds: List<Long>,
)

internal data class NewPipeBackup(
    val subscriptions: List<NewPipeSubscription>,
    val streams: Map<Long, NewPipeStream>,
    val history: List<NewPipeHistoryEntry>,
    val playlists: List<NewPipePlaylistEntry>,
) {
    fun subscriptionChannels(importedDescription: String): List<ChannelUiModel> = subscriptions
        .distinctBy { it.url }
        .map { subscription ->
            ChannelUiModel(
                id = subscription.url,
                name = subscription.name.ifBlank { subscription.url.hostLabel() },
                sourceId = inferNewPipeSourceId(subscription.url),
                source = "",
                unreadCount = 0,
                followerCount = subscription.subscriberCount?.let(::formatNewPipeCompactCount).orEmpty(),
                description = subscription.description.ifBlank { importedDescription },
                thumbnailUrl = subscription.avatarUrl,
            )
        }

    fun buildImportLibrary(
        includePlaylists: Boolean,
        includeHistory: Boolean,
        importedDescription: String,
    ): Pair<List<VideoUiModel>, List<PlaylistUiModel>> {
        val importedVideos = linkedMapOf<String, VideoUiModel>()

        fun merge(stream: NewPipeStream, historyEntry: NewPipeHistoryEntry? = null) {
            val progress = historyEntry?.let { entry ->
                when {
                    stream.durationSeconds > 0L -> entry.progressMs.toFloat()
                        .div(stream.durationSeconds * 1_000L)
                        .coerceIn(0f, 1f)
                    entry.progressMs > 0L -> 0.01f
                    else -> 0f
                }
            } ?: 0f
            val video = stream.toVideo(
                watchProgress = progress,
                lastWatchedAt = historyEntry?.accessedAtMs?.coerceAtLeast(0L) ?: 0L,
                importedDescription = importedDescription,
            )
            val existing = importedVideos[video.id]
            importedVideos[video.id] = if (existing == null) video else existing.copy(
                watchProgress = if (video.lastWatchedAt >= existing.lastWatchedAt) {
                    video.watchProgress
                } else {
                    existing.watchProgress
                },
                lastWatchedAt = maxOf(existing.lastWatchedAt, video.lastWatchedAt),
            )
        }

        val importedPlaylists = if (includePlaylists) playlists.mapNotNull { playlist ->
            val availableStreams = playlist.streamIds.mapNotNull(streams::get)
            availableStreams.forEach { merge(it) }
            availableStreams.takeIf(List<*>::isNotEmpty)?.let {
                PlaylistUiModel(
                    id = "newpipe-playlist-${playlist.id}",
                    title = playlist.name.ifBlank { "Imported playlist" },
                    description = importedDescription,
                    videoIds = availableStreams.map(NewPipeStream::url),
                )
            }
        } else {
            emptyList()
        }

        if (includeHistory) history.forEach { entry ->
            streams[entry.streamId]?.let { merge(it, entry) }
        }
        return importedVideos.values.toList() to importedPlaylists
    }
}

internal open class NewPipeBackupException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

internal object NewPipeBackupParser {
    fun parse(data: ByteArray, cacheDirectory: File): NewPipeBackup {
        if (data.isEmpty()) throw NewPipeBackupException("The selected NewPipe export is empty.")
        if (data.size > MAX_INPUT_BYTES) {
            throw NewPipeBackupException("The selected NewPipe export is larger than 256 MB.")
        }
        val jsonOffset = if (data.hasPrefix(UTF8_BOM)) UTF8_BOM.size else 0
        var firstContentByte: Byte? = null
        for (index in jsonOffset until data.size) {
            val value = data[index]
            if (!value.toInt().toChar().isWhitespace()) {
                firstContentByte = value
                break
            }
        }
        return when {
            firstContentByte == '{'.code.toByte() -> parseSubscriptionJson(data)
            data.hasPrefix(SQLITE_MAGIC) -> parseDatabaseFile(data, cacheDirectory)
            data.isZip() -> parseZip(data, cacheDirectory)
            else -> throw NewPipeBackupException(
                "This is not a NewPipe export. Select its export ZIP, newpipe.db, or subscriptions.json.",
            )
        }
    }

    private fun parseSubscriptionJson(data: ByteArray): NewPipeBackup {
        val root = runCatching {
            JsonParser.parseString(data.toString(Charsets.UTF_8).trimStart('\uFEFF'))
        }
            .getOrElse { throw NewPipeBackupException("The NewPipe subscriptions JSON is invalid.", it) }
        val array = root.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.get("subscriptions")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?: throw NewPipeBackupException("The JSON does not contain NewPipe subscriptions.")
        if (array.size() > MAX_SUBSCRIPTIONS) {
            throw NewPipeBackupException("The NewPipe export contains too many subscriptions.")
        }
        val subscriptions = array.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val url = item.string("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            NewPipeSubscription(
                serviceId = item.int("service_id"),
                url = url,
                name = item.string("name"),
                avatarUrl = item.string("avatar_url"),
                subscriberCount = item.nullableLong("subscriber_count"),
                description = item.string("description"),
            )
        }.distinctBy(NewPipeSubscription::url)
        return NewPipeBackup(subscriptions, emptyMap(), emptyList(), emptyList())
    }

    private fun parseZip(data: ByteArray, cacheDirectory: File): NewPipeBackup {
        var databaseBytes: ByteArray? = null
        var entries = 0
        var totalUncompressedBytes = 0L
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                entries += 1
                if (entries > MAX_ZIP_ENTRIES) {
                    throw NewPipeBackupException("The NewPipe export contains too many files.")
                }
                val isDatabase = entry.name.substringAfterLast('/').equals("newpipe.db", ignoreCase = true)
                if (isDatabase) {
                    if (databaseBytes != null) {
                        throw NewPipeBackupException("The NewPipe export contains more than one database.")
                    }
                }
                val output = if (isDatabase) java.io.ByteArrayOutputStream() else null
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entryBytes = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytes += read
                    totalUncompressedBytes += read
                    if (entryBytes > MAX_DATABASE_BYTES || totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw NewPipeBackupException("The NewPipe export expands beyond the safe import limit.")
                    }
                    output?.write(buffer, 0, read)
                }
                if (isDatabase) databaseBytes = output?.toByteArray()
            }
        }
        val bytes = databaseBytes
            ?: throw NewPipeBackupException("The NewPipe export does not contain newpipe.db.")
        return parseDatabaseFile(bytes, cacheDirectory)
    }

    private fun parseDatabaseFile(data: ByteArray, cacheDirectory: File): NewPipeBackup {
        if (!data.hasPrefix(SQLITE_MAGIC)) {
            throw NewPipeBackupException("The NewPipe database is invalid.")
        }
        cacheDirectory.mkdirs()
        val file = File.createTempFile("newpipe-import-", ".db", cacheDirectory)
        return try {
            FileOutputStream(file).use { it.write(data) }
            val database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            try {
                parseDatabase(database)
            } finally {
                database.close()
            }
        } catch (error: NewPipeBackupException) {
            throw error
        } catch (error: Throwable) {
            throw NewPipeBackupException("The NewPipe database could not be read.", error)
        } finally {
            file.delete()
            File(file.absolutePath + "-journal").delete()
            File(file.absolutePath + "-wal").delete()
            File(file.absolutePath + "-shm").delete()
        }
    }

    private fun parseDatabase(database: SQLiteDatabase): NewPipeBackup {
        if (!database.hasTable("subscriptions") && !database.hasTable("streams")) {
            throw NewPipeBackupException("This database does not contain NewPipe data.")
        }
        val subscriptions = database.readSubscriptions()
        val streams = database.readStreams()
        val progressByStream = database.readStreamProgress()
        val history = database.readHistory(progressByStream)
        val playlists = database.readPlaylists()
        return NewPipeBackup(subscriptions, streams.associateBy(NewPipeStream::id), history, playlists)
    }

    private fun SQLiteDatabase.readSubscriptions(): List<NewPipeSubscription> {
        if (!hasTable("subscriptions")) return emptyList()
        val columns = tableColumns("subscriptions")
        return rawQuery("SELECT * FROM `subscriptions` LIMIT $MAX_SUBSCRIPTIONS", null).useRows { cursor ->
            val url = cursor.string("url")
            url.takeIf(String::isNotBlank)?.let {
                NewPipeSubscription(
                    serviceId = cursor.int("service_id"),
                    url = it,
                    name = cursor.string("name"),
                    avatarUrl = cursor.string("avatar_url", columns),
                    subscriberCount = cursor.nullableLong("subscriber_count", columns),
                    description = cursor.string("description", columns),
                )
            }
        }.distinctBy(NewPipeSubscription::url)
    }

    private fun SQLiteDatabase.readStreams(): List<NewPipeStream> {
        if (!hasTable("streams")) return emptyList()
        val columns = tableColumns("streams")
        return rawQuery("SELECT * FROM `streams` LIMIT $MAX_STREAMS", null).useRows { cursor ->
            val url = cursor.string("url")
            url.takeIf(String::isNotBlank)?.let {
                NewPipeStream(
                    id = cursor.long("uid"),
                    serviceId = cursor.int("service_id"),
                    url = it,
                    title = cursor.string("title").ifBlank { it },
                    streamType = cursor.int("stream_type", columns),
                    durationSeconds = cursor.long("duration", columns),
                    uploader = cursor.string("uploader", columns),
                    uploaderUrl = cursor.string("uploader_url", columns),
                    thumbnailUrl = cursor.string("thumbnail_url", columns),
                    viewCount = cursor.nullableLong("view_count", columns),
                )
            }
        }.distinctBy(NewPipeStream::id)
    }

    private fun SQLiteDatabase.readStreamProgress(): Map<Long, Long> {
        if (!hasTable("stream_state")) return emptyMap()
        val columns = tableColumns("stream_state")
        if ("stream_id" !in columns || "progress_time" !in columns) return emptyMap()
        return rawQuery(
            "SELECT `stream_id`, MAX(`progress_time`) AS `progress_time` FROM `stream_state` " +
                "GROUP BY `stream_id` LIMIT $MAX_HISTORY",
            null,
        ).useRows { cursor -> cursor.long("stream_id") to cursor.long("progress_time") }.toMap()
    }

    private fun SQLiteDatabase.readHistory(progressByStream: Map<Long, Long>): List<NewPipeHistoryEntry> {
        if (!hasTable("stream_history")) return emptyList()
        val columns = tableColumns("stream_history")
        if ("stream_id" !in columns || "access_date" !in columns) return emptyList()
        return rawQuery(
            "SELECT `stream_id`, MAX(`access_date`) AS `access_date` FROM `stream_history` " +
                "GROUP BY `stream_id` ORDER BY `access_date` DESC LIMIT $MAX_HISTORY",
            null,
        ).useRows { cursor ->
            val streamId = cursor.long("stream_id")
            NewPipeHistoryEntry(
                streamId = streamId,
                accessedAtMs = cursor.long("access_date"),
                progressMs = progressByStream[streamId] ?: 0L,
            )
        }
    }

    private fun SQLiteDatabase.readPlaylists(): List<NewPipePlaylistEntry> {
        if (!hasTable("playlists") || !hasTable("playlist_stream_join")) return emptyList()
        val playlistColumns = tableColumns("playlists")
        val joinColumns = tableColumns("playlist_stream_join")
        if ("uid" !in playlistColumns || "name" !in playlistColumns ||
            "playlist_id" !in joinColumns || "stream_id" !in joinColumns
        ) return emptyList()
        val order = if ("display_index" in playlistColumns) "`display_index`, `uid`" else "`uid`"
        val playlists = rawQuery(
            "SELECT `uid`, `name` FROM `playlists` ORDER BY $order LIMIT $MAX_PLAYLISTS",
            null,
        ).useRows { cursor -> cursor.long("uid") to cursor.string("name") }
        return playlists.map { (playlistId, name) ->
            val joinOrder = if ("join_index" in joinColumns) "`join_index`" else "rowid"
            val streamIds = rawQuery(
                "SELECT `stream_id` FROM `playlist_stream_join` WHERE `playlist_id` = ? " +
                    "ORDER BY $joinOrder LIMIT $MAX_PLAYLIST_STREAMS",
                arrayOf(playlistId.toString()),
            ).useRows { it.long("stream_id") }
            NewPipePlaylistEntry(playlistId, name, streamIds)
        }
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private fun SQLiteDatabase.tableColumns(table: String): Set<String> =
        rawQuery("PRAGMA table_info(`${table.replace("`", "``")}`)", null).useRows {
            it.string("name")
        }.toSet()

    private inline fun <T> Cursor.useRows(block: (Cursor) -> T?): List<T> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) block(cursor)?.let(::add)
        }
    }

    private fun Cursor.index(name: String): Int = getColumnIndex(name)
    private fun Cursor.string(name: String): String = index(name).takeIf { it >= 0 && !isNull(it) }
        ?.let(::getString).orEmpty()
    private fun Cursor.string(name: String, columns: Set<String>): String =
        if (name in columns) string(name) else ""
    private fun Cursor.int(name: String): Int = index(name).takeIf { it >= 0 && !isNull(it) }
        ?.let(::getInt) ?: 0
    private fun Cursor.int(name: String, columns: Set<String>): Int =
        if (name in columns) int(name) else 0
    private fun Cursor.long(name: String): Long = index(name).takeIf { it >= 0 && !isNull(it) }
        ?.let(::getLong) ?: 0L
    private fun Cursor.long(name: String, columns: Set<String>): Long =
        if (name in columns) long(name) else 0L
    private fun Cursor.nullableLong(name: String, columns: Set<String>): Long? =
        if (name !in columns) null else index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private fun ByteArray.isZip(): Boolean = size >= 2 && this[0] == 0x50.toByte() && this[1] == 0x4B.toByte()
    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val MAX_INPUT_BYTES = 256 * 1024 * 1024
    private const val MAX_DATABASE_BYTES = 256L * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 320L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 64
    private const val MAX_SUBSCRIPTIONS = 50_000
    private const val MAX_STREAMS = 250_000
    private const val MAX_HISTORY = 250_000
    private const val MAX_PLAYLISTS = 20_000
    private const val MAX_PLAYLIST_STREAMS = 250_000
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
}

private fun NewPipeStream.toVideo(
    watchProgress: Float,
    lastWatchedAt: Long,
    importedDescription: String,
): VideoUiModel = VideoUiModel(
    id = url,
    title = title,
    creator = uploader.ifBlank { "Unknown creator" },
    metadata = viewCount?.let { "${formatNewPipeCompactCount(it)} views" }.orEmpty()
        .ifBlank { importedDescription },
    duration = formatNewPipeDuration(durationSeconds),
    channelId = uploaderUrl,
    sourceId = inferNewPipeSourceId(url),
    isLive = durationSeconds < 0L,
    watchProgress = watchProgress,
    lastWatchedAt = lastWatchedAt,
    contentUrl = url,
    thumbnailUrl = thumbnailUrl,
    shareUrl = url,
    authorUrl = uploaderUrl,
)

private fun inferNewPipeSourceId(url: String): String = when {
    "youtube.com" in url || "youtu.be" in url -> "youtube"
    "soundcloud.com" in url -> "soundcloud"
    "media.ccc.de" in url -> "media-ccc"
    "peertube" in url -> "peertube"
    "bandcamp.com" in url -> "bandcamp"
    "bilibili.com" in url -> "bilibili"
    else -> "youtube"
}

private fun String.hostLabel(): String = substringAfter("://").substringBefore('/').ifBlank { this }

private fun formatNewPipeDuration(seconds: Long): String {
    if (seconds <= 0L) return ""
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

private fun formatNewPipeCompactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).trimEnd('0').trimEnd('.')
    else -> value.toString()
}

private fun com.google.gson.JsonObject.string(key: String): String = get(key)
    ?.takeUnless(JsonElement::isJsonNull)
    ?.let { runCatching { it.asString }.getOrDefault("") }
    .orEmpty()

private fun com.google.gson.JsonObject.int(key: String): Int = get(key)
    ?.let { runCatching { it.asInt }.getOrDefault(0) }
    ?: 0

private fun com.google.gson.JsonObject.nullableLong(key: String): Long? = get(key)
    ?.takeUnless(JsonElement::isJsonNull)
    ?.let { runCatching { it.asLong }.getOrNull() }
