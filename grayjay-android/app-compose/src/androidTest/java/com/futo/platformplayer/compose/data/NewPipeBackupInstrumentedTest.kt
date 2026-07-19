package com.futo.platformplayer.compose.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeBackupInstrumentedTest {
    @Test
    fun readsSubscriptionsHistoryProgressAndOrderedPlaylistsFromExportZip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "newpipe-fixture.db")
        SQLiteDatabase.deleteDatabase(source)
        SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
            database.execSQL(
                "CREATE TABLE subscriptions (uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, name TEXT NOT NULL, avatar_url TEXT, subscriber_count INTEGER, " +
                    "description TEXT, notification_mode INTEGER)",
            )
            database.execSQL(
                "INSERT INTO subscriptions VALUES " +
                    "(1, 0, 'https://www.youtube.com/channel/test', 'Test creator', " +
                    "'https://example.com/avatar.jpg', 1234, 'Description', 0)",
            )
            database.execSQL(
                "CREATE TABLE streams (uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, url TEXT NOT NULL, " +
                    "title TEXT NOT NULL, stream_type INTEGER NOT NULL, duration INTEGER NOT NULL, uploader TEXT, " +
                    "uploader_url TEXT, thumbnail_url TEXT, view_count INTEGER)",
            )
            database.execSQL(
                "INSERT INTO streams VALUES " +
                    "(10, 0, 'https://www.youtube.com/watch?v=first', 'First', 0, 200, 'Test creator', " +
                    "'https://www.youtube.com/channel/test', 'https://example.com/first.jpg', 5000), " +
                    "(11, 0, 'https://www.youtube.com/watch?v=second', 'Second', 0, 100, 'Test creator', " +
                    "'https://www.youtube.com/channel/test', 'https://example.com/second.jpg', 6000)",
            )
            database.execSQL("CREATE TABLE stream_history (stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, repeat_count INTEGER)")
            database.execSQL("INSERT INTO stream_history VALUES (10, 1700000000000, 1)")
            database.execSQL("CREATE TABLE stream_state (stream_id INTEGER PRIMARY KEY, progress_time INTEGER NOT NULL)")
            database.execSQL("INSERT INTO stream_state VALUES (10, 50000)")
            database.execSQL("CREATE TABLE playlists (uid INTEGER PRIMARY KEY, name TEXT NOT NULL, display_index INTEGER)")
            database.execSQL("INSERT INTO playlists VALUES (4, 'Fixture playlist', 0)")
            database.execSQL("CREATE TABLE playlist_stream_join (playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, join_index INTEGER NOT NULL)")
            database.execSQL("INSERT INTO playlist_stream_join VALUES (4, 11, 0), (4, 10, 1)")
        }
        val zipBytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("NewPipeData/newpipe.db"))
                zip.write(source.readBytes())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        val backup = NewPipeBackupParser.parse(zipBytes, context.cacheDir)
        val (videos, playlists) = backup.buildImportLibrary(true, true, "Imported")

        assertEquals("Test creator", backup.subscriptions.single().name)
        assertEquals(listOf(11L, 10L), backup.playlists.single().streamIds)
        assertEquals(0.25f, videos.single { it.id.endsWith("first") }.watchProgress, 0.0001f)
        assertEquals(listOf(
            "https://www.youtube.com/watch?v=second",
            "https://www.youtube.com/watch?v=first",
        ), playlists.single().videoIds)
        assertTrue(source.delete())
    }
}
