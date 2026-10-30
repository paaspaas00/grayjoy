package com.futo.platformplayer.compose.recognition

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

class SongFingerprintTest {
    @Test fun visualizationReusesTheFftWithoutChangingTheSignature() {
        val fingerprint = SongFingerprint()
        val signal = ShortArray(8192) { (kotlin.math.sin(2 * Math.PI * 440 * it / 16000) * 4000).toInt().toShort() }
        fingerprint.feed(signal)
        val before = fingerprint.encode()
        val levels = fingerprint.visualSpectrum()
        assertEquals(15, levels.size)
        assertTrue(levels.all { it.isFinite() && it in 0f..1f })
        assertTrue(levels.indices.maxBy { levels[it] } in 5..7)
        assertArrayEquals(before, fingerprint.encode())
    }
    @Test fun deterministicPcmMatchesSongRecReferenceSignature() {
        var state = 42L
        val samples = ShortArray(96000) { state = (state * 1664525 + 1013904223) and 0xffffffffL; ((state ushr 16) - 32768).toShort() }
        val fingerprint = SongFingerprint(); fingerprint.feed(samples)
        val bytes = fingerprint.encode()
        assertEquals(6000, fingerprint.durationMs)
        assertEquals(4852, bytes.size)
        assertEquals("39b766910cf95dbfd176e4c344df8eca0674f0e31aebd34979e8233414cb4d3a",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }
    @Test fun silentInputIsBoundedAndHeaderChecksumIsValid() {
        val fingerprint = SongFingerprint(); repeat(10) { fingerprint.feed(ShortArray(2048)) }
        val bytes = fingerprint.encode(); val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(56, bytes.size); assertEquals(0xcafe2580.toInt(), header.getInt(0))
        assertEquals(8, header.getInt(8))
        val crc = CRC32(); crc.update(bytes, 8, bytes.size - 8)
        assertEquals(crc.value.toInt(), header.getInt(4))
    }
    @Test fun malformedResultsAndUnsafeArtworkAreNotAccepted() {
        assertNull(parseRecognizedSong(JSONObject()))
        assertNull(parseRecognizedSong(JSONObject("{\"track\":{\"title\":\"\"}}")))
        val song = parseRecognizedSong(JSONObject("{\"track\":{\"title\":\"Song\",\"subtitle\":\"Artist\",\"images\":{\"coverart\":\"file:///private\"}}}"))!!
        assertEquals("Artist Song", song.query); assertEquals("", song.artwork)
    }
}
