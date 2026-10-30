// SPDX-License-Identifier: GPL-3.0-or-later
// Kotlin adaptation of SongRec's fingerprint algorithm by marin-m and contributors.
// https://github.com/marin-m/SongRec (00d457db6dcb8398384d197e63f017baf9c0095d)
package com.futo.platformplayer.compose.recognition

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.*

/** 16 kHz mono PCM -> frequency peaks. No raw audio is included in the signature. */
internal class SongFingerprint {
    private data class Peak(val pass: Int, val magnitude: Int, val bin: Int)
    private val samples = DoubleArray(2048)
    private val fft = Array(256) { DoubleArray(1025) }
    private val spread = Array(256) { DoubleArray(1025) }
    private val window = DoubleArray(2048) { .5 - .5 * cos(2 * PI * (it + 1) / 2049) }
    private val re = DoubleArray(2048)
    private val im = DoubleArray(2048)
    private val peaks = Array(4) { mutableListOf<Peak>() }
    private var position = 0
    private var passes = 0
    private var count = 0

    fun feed(input: ShortArray, size: Int = input.size) {
        require(size in 0..input.size && size % 128 == 0)
        require(count + size <= 16000 * 24)
        for (start in 0 until size step 128) {
            for (i in 0 until 128) samples[(position + i) % 2048] = input[start + i].toDouble()
            position = (position + 128) % 2048
            for (i in re.indices) { re[i] = samples[(position + i) % 2048] * window[i]; im[i] = 0.0 }
            transform(re, im)
            val current = fft[passes % 256]
            for (i in current.indices) current[i] = max(1e-10, (re[i] * re[i] + im[i] * im[i]) / 131072)
            val s = spread[passes % 256]
            current.copyInto(s)
            for (i in s.indices) {
                if (i < 1023) s[i] = maxOf(s[i], s[i + 1], s[i + 2])
                var value = s[i]
                for (offset in intArrayOf(-1, -3, -6)) {
                    val former = spread[(passes + offset).mod(256)]
                    value = max(value, former[i]); former[i] = value
                }
            }
            passes++; count += 128
            if (passes >= 46) recognize()
        }
    }

    private fun recognize() {
        val a = fft[(passes - 46).mod(256)]
        val b = spread[(passes - 49).mod(256)]
        for (bin in 10 until 1015) {
            if (a[bin] < 1.0 / 64 || a[bin] < b[bin - 1]) continue
            var neighbor = 0.0
            for (o in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) neighbor = max(neighbor, b[bin + o])
            if (a[bin] <= neighbor) continue
            for (o in intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249))
                neighbor = max(neighbor, spread[(passes + o).mod(256)][bin - 1])
            if (a[bin] <= neighbor) continue
            fun magnitude(i: Int) = ln(max(1.0 / 64, a[i])) * 1477.3 + 6144
            val m = magnitude(bin)
            val left = magnitude(bin - 1); val right = magnitude(bin + 1)
            val curvature = 2 * m - left - right
            if (curvature <= 0) continue
            val corrected = bin * 64 + (right - left) * 32 / curvature
            val hz = corrected * 16000 / 2 / 1024 / 64
            val band = when { hz < 250 -> -1; hz < 520 -> 0; hz < 1450 -> 1; hz < 3500 -> 2; hz <= 5500 -> 3; else -> -1 }
            if (band >= 0) peaks[band].add(Peak(passes - 46, m.toInt().coerceIn(0, 65535), corrected.toInt().coerceIn(0, 65535)))
        }
    }

    /** Reuses the current FFT for the UI; the signature and captured audio are unchanged. */
    fun visualSpectrum(): FloatArray {
        if (passes == 0) return FloatArray(15)
        val power = fft[(passes - 1).mod(256)]
        val result = DoubleArray(15)
        val ratio = ln(6000.0 / 70)
        for (i in result.indices) {
            val low = (70 * exp(ratio * i / 15) / 7.8125).toInt().coerceIn(1, 1024)
            val high = ceil(70 * exp(ratio * (i + 1) / 15) / 7.8125).toInt().coerceIn(low + 1, 1025)
            for (bin in low until high) result[i] = max(result[i], power[bin])
        }
        val peak = result.maxOrNull() ?: 0.0
        if (peak <= 1e-8) return FloatArray(15)
        return FloatArray(15) { (result[it] / peak).coerceIn(0.0, 1.0).pow(.45).toFloat() }
    }

    fun encode(): ByteArray {
        val body = ByteArrayOutputStream()
        fun ByteArrayOutputStream.intLE(n: Int) { write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()) }
        for (band in peaks.indices) {
            if (peaks[band].isEmpty()) continue
            val data = ByteArrayOutputStream(); var previous = 0
            for (peak in peaks[band]) {
                if (peak.pass - previous >= 255) { data.write(255); data.intLE(peak.pass); previous = peak.pass }
                data.write(peak.pass - previous)
                data.write(peak.magnitude and 255); data.write(peak.magnitude shr 8)
                data.write(peak.bin and 255); data.write(peak.bin shr 8)
                previous = peak.pass
            }
            body.intLE(0x60030040 + band); body.intLE(data.size()); body.write(data.toByteArray())
            repeat((-data.size()).mod(4)) { body.write(0) }
        }
        val buffer = ByteBuffer.allocate(56 + body.size()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0xcafe2580.toInt()).putInt(0).putInt(body.size() + 8).putInt(0x94119c00.toInt())
        repeat(3) { buffer.putInt(0) }; buffer.putInt(3 shl 27); repeat(2) { buffer.putInt(0) }
        buffer.putInt(count + 3840).putInt(0x7c0000).putInt(0x40000000).putInt(body.size() + 8).put(body.toByteArray())
        val bytes = buffer.array(); val crc = CRC32(); crc.update(bytes, 8, bytes.size - 8)
        buffer.putInt(4, crc.value.toInt())
        return bytes
    }
    fun uri() = "data:audio/vnd.shazam.sig;base64," + Base64.getEncoder().encodeToString(encode())
    val durationMs get() = count / 16

    private fun transform(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }; j = j xor bit
            if (i < j) { val t = real[i]; real[i] = real[j]; real[j] = t }
        }
        var length = 2
        while (length <= real.size) {
            val angle = -2 * PI / length; val wr = cos(angle); val wi = sin(angle)
            for (start in real.indices step length) {
                var ur = 1.0; var ui = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k; val b = a + length / 2
                    val vr = real[b] * ur - imaginary[b] * ui; val vi = real[b] * ui + imaginary[b] * ur
                    real[b] = real[a] - vr; imaginary[b] = imaginary[a] - vi; real[a] += vr; imaginary[a] += vi
                    val next = ur * wr - ui * wi; ui = ur * wi + ui * wr; ur = next
                }
            }
            length *= 2
        }
    }
}
