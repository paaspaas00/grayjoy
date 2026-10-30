package com.futo.platformplayer.compose.recognition

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in live interoperability test; the external fixture is not bundled with the application. */
class SongRecognitionLiveTest {
    @Test fun recognizeExternalReferenceFixture() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.getExternalFilesDir(null), "recognition-fixture.pcm")
        assumeTrue("Provide the SongRec reference test excerpt explicitly", fixture.isFile)
        val bytes = fixture.readBytes(); require(bytes.size <= 768000)
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val pcm = ShortArray((input.remaining() / 128) * 128); input.get(pcm)
        val fingerprint = SongFingerprint(); fingerprint.feed(pcm)
        val song = SongRecognitionClient().lookup(fingerprint)
        assertNotNull("Reference music should be recognized by the service", song)
        assertTrue(song!!.title.isNotBlank()); assertTrue(song.artist.isNotBlank())
        println("Recognized public fixture: ${song.artist} - ${song.title}")
    }
}
