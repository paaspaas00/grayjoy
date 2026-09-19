package com.futo.platformplayer.compose.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class LibraryExportFormat { Grayjay, NewPipe }

internal fun safeExportProgress(value: Float): Double =
    if (value.isFinite()) value.toDouble().coerceIn(0.0, 1.0) else 0.0

internal fun exportDurationSeconds(duration: String): Long {
    val fields = duration.split(':')
    if (fields.size !in 1..3) return 0L
    var seconds = 0L
    for ((index, field) in fields.withIndex()) {
        val number = field.toLongOrNull()?.takeIf { it >= 0 } ?: return 0L
        if (index > 0 && number >= 60) return 0L
        if (seconds > (Long.MAX_VALUE / 1000L - number) / 60L) return 0L
        seconds = seconds * 60L + number
    }
    return seconds.takeIf { it <= Long.MAX_VALUE / 1000L } ?: 0L
}

internal class LibraryBackupExporter(private val context: Context) {
    fun export(
        format: LibraryExportFormat,
        videos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
        channels: List<ChannelUiModel>,
        output: OutputStream,
        checkActive: () -> Unit = {},
    ) {
        checkActive()
        if (format == LibraryExportFormat.NewPipe) {
            (videos.map { it.sourceId } + channels.map { it.sourceId }).distinct()
                .forEach { it.newPipeServiceId() }
        }
        when (format) {
            LibraryExportFormat.Grayjay -> exportGrayjay(videos, playlists, channels, output, checkActive)
            LibraryExportFormat.NewPipe -> exportNewPipe(videos, playlists, channels, output, checkActive)
        }
    }

    private fun exportGrayjay(
        videos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
        channels: List<ChannelUiModel>,
        output: OutputStream,
        checkActive: () -> Unit,
    ) {
        val byId = videos.associateBy(VideoUiModel::id)
        ZipOutputStream(output.buffered()).use { zip ->
            zip.writeTextEntry(
                "exportInfo",
                JSONObject()
                    .put("version", 1)
                    .put("app", "Grayjoy")
                    .put("createdAt", System.currentTimeMillis())
                    .toString(),
            )
            zip.writeTextEntry(
                "stores/subscriptions",
                JSONArray(channels.map(ChannelUiModel::id).filter(String::isNotBlank)).toString(),
            )
            zip.writeTextEntry(
                "stores/watch_later",
                JSONArray(
                    videos.filter(VideoUiModel::isWatchLater).map { it.portableUrl() },
                ).toString(),
            )
            zip.writeTextEntry(
                "stores/history",
                JSONArray(
                    videos.filter { it.lastWatchedAt > 0L }.map { video ->
                        checkActive()
                        val durationSeconds = video.durationSeconds()
                        val positionSeconds = (durationSeconds * safeExportProgress(video.watchProgress))
                            .toLong()
                            .coerceAtLeast(0L)
                        listOf(
                            video.portableUrl(),
                            video.lastWatchedAt / 1_000L,
                            positionSeconds,
                            video.title,
                        ).joinToString("|||")
                    },
                ).toString(),
            )
            zip.writeTextEntry(
                "stores/playlists",
                JSONArray(
                    playlists.map { playlist ->
                        checkActive()
                        buildString {
                            append(playlist.title)
                            append(":::")
                            append(playlist.id)
                            playlist.videoIds.forEach { id ->
                                append('\n')
                                append(byId[id]?.portableUrl() ?: id)
                            }
                        }
                    },
                ).toString(),
            )
            zip.writeTextEntry(
                "cache_videos",
                JSONArray().apply { videos.forEach { checkActive(); put(it.toGrayjayCacheJson()) } }.toString(),
            )
            zip.writeTextEntry(
                "cache_channels",
                JSONArray().apply { channels.forEach { put(it.toGrayjayCacheJson()) } }.toString(),
            )
            zip.writeTextEntry("plugins", "{}")
            zip.writeTextEntry("plugin_settings", "{}")
            zip.writeTextEntry("settings", "{}")
        }
    }

    private fun exportNewPipe(
        videos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
        channels: List<ChannelUiModel>,
        output: OutputStream,
        checkActive: () -> Unit,
    ) {
        val databaseFile = File.createTempFile("grayjoy-newpipe-export-", ".db", context.cacheDir)
        try {
            createNewPipeDatabase(databaseFile, videos, playlists, channels, checkActive)
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("newpipe.db"))
                databaseFile.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        } finally {
            databaseFile.delete()
            File(databaseFile.path + "-journal").delete()
            File(databaseFile.path + "-wal").delete()
            File(databaseFile.path + "-shm").delete()
        }
    }

    private fun createNewPipeDatabase(
        destination: File,
        videos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
        channels: List<ChannelUiModel>,
        checkActive: () -> Unit,
    ) {
        destination.delete()
        val database = SQLiteDatabase.openOrCreateDatabase(destination, null)
        try {
            database.beginTransaction()
            NEWPIPE_TABLES.forEach(database::execSQL)
            NEWPIPE_INDICES.forEach(database::execSQL)
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '$NEWPIPE_IDENTITY_HASH')",
            )
            database.version = NEWPIPE_SCHEMA_VERSION

            channels.distinctBy(ChannelUiModel::id).forEach { channel ->
                checkActive()
                database.insertOrThrow(
                    "subscriptions",
                    null,
                    ContentValues().apply {
                        put("service_id", channel.sourceId.newPipeServiceId())
                        put("url", channel.id)
                        put("name", channel.name)
                        put("avatar_url", channel.thumbnailUrl)
                        put("subscriber_count", channel.followerCount.filter(Char::isDigit).toLongOrNull())
                        put("description", channel.description)
                        put("notification_mode", 0)
                    },
                )
            }

            val streamIds = linkedMapOf<String, Long>()
            val idsByUrl = mutableMapOf<Pair<Int, String>, Long>()
            videos.distinctBy(VideoUiModel::id).forEach { video ->
                checkActive()
                val identity = video.sourceId.newPipeServiceId() to video.portableUrl()
                val id = idsByUrl.getOrPut(identity) { database.insertOrThrow(
                    "streams",
                    null,
                    ContentValues().apply {
                        put("service_id", video.sourceId.newPipeServiceId())
                        put("url", video.portableUrl())
                        put("title", video.title)
                        put(
                            "stream_type",
                            when {
                                video.isLive -> "LIVE_STREAM"
                                video.playbackAudioOnly -> "AUDIO_STREAM"
                                else -> "VIDEO_STREAM"
                            },
                        )
                        put("duration", video.durationSeconds())
                        put("uploader", video.creator)
                        put("uploader_url", video.authorUrl.ifBlank { video.channelId })
                        put("thumbnail_url", video.thumbnailUrl)
                        video.viewCount?.let { put("view_count", it) }
                        put("textual_upload_date", video.metadata)
                        video.publishedAtMs.takeIf { it > 0L }?.let { put("upload_date", it) }
                        put("is_upload_date_approximation", 1)
                    },
                ) }
                streamIds[video.id] = id
                if (video.lastWatchedAt > 0L) {
                    database.insertWithOnConflict(
                        "stream_history",
                        null,
                        ContentValues().apply {
                            put("stream_id", id)
                            put("access_date", video.lastWatchedAt)
                            put("repeat_count", 1)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                    database.insertWithOnConflict(
                        "stream_state",
                        null,
                        ContentValues().apply {
                            put("stream_id", id)
                            put(
                                "progress_time",
                                (video.durationSeconds() * 1_000L * safeExportProgress(video.watchProgress))
                                    .toLong()
                                    .coerceAtLeast(0L),
                            )
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }

            playlists.forEachIndexed { displayIndex, playlist ->
                checkActive()
                val playlistId = database.insertOrThrow(
                    "playlists",
                    null,
                    ContentValues().apply {
                        put("name", playlist.title)
                        put("is_thumbnail_permanent", 0)
                        put("thumbnail_stream_id", -1)
                        put("display_index", displayIndex)
                    },
                )
                playlist.videoIds.forEachIndexed { joinIndex, videoId ->
                    checkActive()
                    val streamId = streamIds[videoId] ?: return@forEachIndexed
                    database.insertOrThrow(
                        "playlist_stream_join",
                        null,
                        ContentValues().apply {
                            put("playlist_id", playlistId)
                            put("stream_id", streamId)
                            put("join_index", joinIndex)
                        },
                    )
                }
            }
            database.setTransactionSuccessful()
        } finally {
            if (database.inTransaction()) database.endTransaction()
            database.close()
        }
    }

    private fun ZipOutputStream.writeTextEntry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun VideoUiModel.portableUrl(): String = contentUrl.ifBlank {
        shareUrl.ifBlank { id }
    }

    private fun VideoUiModel.durationSeconds(): Long = exportDurationSeconds(duration)

    private fun VideoUiModel.toGrayjayCacheJson() = JSONObject().apply {
        put("url", portableUrl())
        put("shareUrl", shareUrl.ifBlank { portableUrl() })
        put("name", title)
        put("duration", durationSeconds())
        viewCount?.let { put("viewCount", it) }
        put("id", JSONObject().put("pluginId", sourceId))
        put(
            "author",
            JSONObject()
                .put("name", creator)
                .put("url", authorUrl.ifBlank { channelId })
                .put("thumbnail", authorThumbnailUrl)
                .put("subscribers", authorSubscriberCount ?: -1L)
                .put("id", JSONObject().put("pluginId", sourceId)),
        )
        put(
            "thumbnails",
            JSONObject().put(
                "sources",
                JSONArray().put(JSONObject().put("quality", 1).put("url", thumbnailUrl)),
            ),
        )
    }

    private fun ChannelUiModel.toGrayjayCacheJson() = JSONObject().apply {
        put("url", id)
        put("name", name)
        put("description", description)
        put("thumbnail", thumbnailUrl)
        put("id", JSONObject().put("pluginId", sourceId))
    }

    private fun String.newPipeServiceId(): Int = when (lowercase()) {
        "youtube" -> 0
        "soundcloud" -> 1
        "media_ccc", "mediaccc" -> 2
        "peertube" -> 3
        "bandcamp" -> 4
        "bilibili" -> 5
        else -> error(context.getString(R.string.newpipe_export_unsupported_source))
    }

    private companion object {
        const val NEWPIPE_SCHEMA_VERSION = 9
        const val NEWPIPE_IDENTITY_HASH = "7591e8039faa74d8c0517dc867af9d3e"

        val NEWPIPE_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS subscriptions (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, url TEXT, name TEXT, avatar_url TEXT, subscriber_count INTEGER, description TEXT, notification_mode INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS search_history (creation_date INTEGER, service_id INTEGER NOT NULL, search TEXT, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS streams (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, duration INTEGER NOT NULL, uploader TEXT NOT NULL, uploader_url TEXT, thumbnail_url TEXT, view_count INTEGER, textual_upload_date TEXT, upload_date INTEGER, is_upload_date_approximation INTEGER)",
            "CREATE TABLE IF NOT EXISTS stream_history (stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, repeat_count INTEGER NOT NULL, PRIMARY KEY(stream_id, access_date), FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS stream_state (stream_id INTEGER NOT NULL, progress_time INTEGER NOT NULL, PRIMARY KEY(stream_id), FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS playlists (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, is_thumbnail_permanent INTEGER NOT NULL, thumbnail_stream_id INTEGER NOT NULL, display_index INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS playlist_stream_join (playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, join_index INTEGER NOT NULL, PRIMARY KEY(playlist_id, join_index), FOREIGN KEY(playlist_id) REFERENCES playlists(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
            "CREATE TABLE IF NOT EXISTS remote_playlists (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, name TEXT, url TEXT, thumbnail_url TEXT, uploader TEXT, display_index INTEGER NOT NULL, stream_count INTEGER)",
            "CREATE TABLE IF NOT EXISTS feed (stream_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, PRIMARY KEY(stream_id, subscription_id), FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
            "CREATE TABLE IF NOT EXISTS feed_group (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, icon_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS feed_group_subscription_join (group_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, PRIMARY KEY(group_id, subscription_id), FOREIGN KEY(group_id) REFERENCES feed_group(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
            "CREATE TABLE IF NOT EXISTS feed_last_updated (subscription_id INTEGER NOT NULL, last_updated INTEGER, PRIMARY KEY(subscription_id), FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        )
        val NEWPIPE_INDICES = listOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_subscriptions_service_id_url ON subscriptions(service_id, url)",
            "CREATE INDEX IF NOT EXISTS index_search_history_search ON search_history(search)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_streams_service_id_url ON streams(service_id, url)",
            "CREATE INDEX IF NOT EXISTS index_stream_history_stream_id ON stream_history(stream_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_stream_join_playlist_id_join_index ON playlist_stream_join(playlist_id, join_index)",
            "CREATE INDEX IF NOT EXISTS index_playlist_stream_join_stream_id ON playlist_stream_join(stream_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_remote_playlists_service_id_url ON remote_playlists(service_id, url)",
            "CREATE INDEX IF NOT EXISTS index_feed_subscription_id ON feed(subscription_id)",
            "CREATE INDEX IF NOT EXISTS index_feed_group_sort_order ON feed_group(sort_order)",
            "CREATE INDEX IF NOT EXISTS index_feed_group_subscription_join_subscription_id ON feed_group_subscription_join(subscription_id)",
        )
    }
}
