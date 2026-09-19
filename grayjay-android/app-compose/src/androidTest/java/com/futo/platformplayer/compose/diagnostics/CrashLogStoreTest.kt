package com.futo.platformplayer.compose.diagnostics

import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashLogStoreTest {
    @Test
    fun crashLogIsAlsoWrittenToDownloadsWithReadableTimestamp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val internal = CrashLogStore.writeCrash(
            context,
            Thread.currentThread(),
            IllegalStateException("Crash-log export test"),
        )
        val resolver = context.contentResolver
        val uri = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(internal.name),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else android.content.ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(0),
            )
        }
        try {
            assertNotNull(uri)
        } finally {
            uri?.let { resolver.delete(it, null, null) }
            internal.delete()
        }
    }
}
