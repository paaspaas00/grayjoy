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
