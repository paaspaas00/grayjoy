package com.futo.platformplayer.compose.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class NewPipeStreamImportTest {
    @Test fun cancellationDuringSqliteRowsClosesTheDatabaseAndRemovesScratchFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "cancel-import-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "original.db")
        val scratch = File(directory, "scratch").apply { mkdirs() }
        try {
            SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
                database.execSQL("CREATE TABLE streams(uid INTEGER, url TEXT, title TEXT)")
                database.beginTransaction()
                try {
                    database.compileStatement("INSERT INTO streams VALUES (?, ?, 'Fixture')").use { insert ->
                        repeat(1_000) { index ->
                            insert.bindLong(1, index.toLong())
                            insert.bindString(2, "https://www.youtube.com/watch?v=fixture$index")
                            insert.executeInsert()
                        }
                    }
                    database.setTransactionSuccessful()
                } finally { database.endTransaction() }
            }
            val cancelled = java.util.concurrent.CancellationException("cancelled fixture")
            var checkpoints = 0
            val caught = assertThrows(java.util.concurrent.CancellationException::class.java) {
                source.inputStream().use { input ->
                    NewPipeBackupParser.parse(input, scratch) { if (++checkpoints == 100) throw cancelled }
                }
            }
            assertSame(cancelled, caught)
            assertEquals(100, checkpoints)
            assertTrue(scratch.listFiles().orEmpty().isEmpty())
            assertEquals(1_000, source.inputStream().use { NewPipeBackupParser.parse(it, scratch) }.streams.size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun playlistReferenceBudgetAppliesAcrossTheWholeBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "budget-import-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "original.db")
        val scratch = File(directory, "scratch").apply { mkdirs() }
        try {
            SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
                database.execSQL("CREATE TABLE streams(uid INTEGER, url TEXT, title TEXT)")
                database.execSQL("CREATE TABLE playlists(uid INTEGER, name TEXT)")
                database.execSQL("CREATE TABLE playlist_stream_join(playlist_id INTEGER, stream_id INTEGER, join_index INTEGER)")
                database.execSQL("INSERT INTO playlists VALUES (1, 'First'), (2, 'Second')")
                database.beginTransaction()
                try {
                    database.compileStatement("INSERT INTO playlist_stream_join VALUES (?, 1, ?)").use { insert ->
                        repeat(2) { playlist -> repeat(125_001) { index ->
                            insert.bindLong(1, playlist + 1L)
                            insert.bindLong(2, index.toLong())
                            insert.executeInsert()
                        } }
                    }
                    database.setTransactionSuccessful()
                } finally { database.endTransaction() }
            }
            val error = assertThrows(NewPipeBackupException::class.java) {
                source.inputStream().use { NewPipeBackupParser.parse(it, scratch) }
            }
            assertTrue(error.message.orEmpty().contains("too many playlist entries"))
            assertTrue(scratch.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun importsLargeDatabaseWithoutKeepingExpandedDatabaseInHeap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "import-audit-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "original.db")
        val zip = File(directory, "export.zip")
        val scratch = File(directory, "scratch").apply { mkdirs() }
        try {
            val database = SQLiteDatabase.openOrCreateDatabase(source, null)
            try {
                database.execSQL("CREATE TABLE streams(uid INTEGER, url TEXT, title TEXT)")
                database.execSQL("INSERT INTO streams VALUES (1, 'https://www.youtube.com/watch?v=fixture', 'Fixture')")
                database.execSQL("CREATE TABLE padding(data BLOB)")
                repeat(20) { database.execSQL("INSERT INTO padding VALUES (zeroblob(1048576))") }
            } finally { database.close() }
            assertTrue(source.length() > 20L * 1024 * 1024)
            ZipOutputStream(zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("newpipe.db"))
                source.inputStream().use { it.copyTo(output) }
                output.closeEntry()
            }
            val before = source.length()
            val result = zip.inputStream().use { NewPipeBackupParser.parse(it, scratch) }
            assertEquals("Fixture", result.streams[1]?.title)
            assertEquals(before, source.length())
            assertTrue(scratch.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
