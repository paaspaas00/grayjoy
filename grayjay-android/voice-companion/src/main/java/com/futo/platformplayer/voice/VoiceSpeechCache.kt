package com.futo.platformplayer.voice

import java.io.*
import java.security.MessageDigest
import java.util.zip.CRC32

internal data class CachedVoiceAudio(val samples: FloatArray, val sampleRate: Int)

/** Stores only an allowlist of synthetic stock replies, never microphone audio or user names. */
internal class VoiceSpeechCache(private val root: File, identity: String, private val allowed: Set<String>,
    private val budget: Long = 64L * 1024 * 1024, private val reserve: Long = 128L * 1024 * 1024) {
    private val directory = File(root, digest(identity))
    private fun target(text: String) = File(directory, "${digest(text)}.gja")
    fun read(text: String): CachedVoiceAudio? {
        if (text !in allowed) return null
        val file = target(text)
        return try {
            if (!file.isFile) return null
            DataInputStream(file.inputStream().buffered(64 * 1024)).use { input ->
                check(input.readInt() == MAGIC && input.readInt() == 1)
                val rate = input.readInt(); val count = input.readInt(); val expected = input.readLong()
                check(rate in 8_000..96_000 && count in 1..rate * 30 && file.length() == 24L + count * 4L)
                val crc = CRC32()
                val samples = FloatArray(count) {
                    val bits = input.readInt(); updateCrc(crc, bits)
                    Float.fromBits(bits).also { check(it.isFinite() && it in -1f..1f) }
                }
                check(crc.value == expected)
                file.setLastModified(System.currentTimeMillis())
                CachedVoiceAudio(samples, rate)
            }
        } catch (_: Exception) { file.delete(); null }
    }
    fun write(text: String, audio: CachedVoiceAudio) {
        if (text !in allowed || audio.sampleRate !in 8_000..96_000 || audio.samples.size !in 1..audio.sampleRate * 30 ||
            audio.samples.any { !it.isFinite() || it !in -1f..1f }) return
        synchronized(writeLock) {
            var temporary: File? = null
            try {
                directory.mkdirs()
                val size = 24L + audio.samples.size * 4L
                if (size > budget || root.usableSpace < reserve + size) return
                val file = target(text)
                temporary = File.createTempFile("speech-", ".tmp", directory)
                val crc = CRC32(); audio.samples.forEach { updateCrc(crc, it.toBits()) }
                FileOutputStream(temporary).use { stream ->
                    val output = DataOutputStream(BufferedOutputStream(stream, 64 * 1024))
                    output.writeInt(MAGIC); output.writeInt(1); output.writeInt(audio.sampleRate)
                    output.writeInt(audio.samples.size); output.writeLong(crc.value)
                    audio.samples.forEach { output.writeInt(it.toBits()) }
                    output.flush(); stream.fd.sync()
                }
                try { java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                var total = 0L
                root.walkTopDown().filter { it.isFile && it.extension == "gja" }.toList()
                    .sortedByDescending(File::lastModified).forEach {
                        total += it.length(); if (total > budget) it.delete()
                    }
            } catch (_: IOException) { /* An optional cache must not prevent spoken feedback. */ }
            finally { temporary?.delete() }
        }
    }
    companion object {
        private const val MAGIC = 0x474A5453
        private val writeLock = Any()
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        private fun updateCrc(crc: CRC32, bits: Int) {
            crc.update(bits ushr 24); crc.update(bits ushr 16); crc.update(bits ushr 8); crc.update(bits)
        }
    }
}
