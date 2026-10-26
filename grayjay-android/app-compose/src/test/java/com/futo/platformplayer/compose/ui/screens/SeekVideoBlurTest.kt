package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SeekVideoBlurTest {
    @Test fun softwareBlurPreservesSolidColorsIncludingSmallFrames() {
        for ((w, h) in listOf(1 to 1, 2 to 5, 32 to 18)) {
            val pixels = IntArray(w * h) { 0xffa73968.toInt() }
            blurSeekPixels(pixels, w, h, 4)
            assertTrue(pixels.all { it == 0xffa73968.toInt() })
        }
    }
    @Test fun softwareBlurSoftensEdgesWithoutMakingTheFrameTransparent() {
        val pixels = IntArray(32 * 18) { if (it % 32 < 16) -1 else 0xff000000.toInt() }
        blurSeekPixels(pixels, 32, 18, 4)
        assertTrue(pixels.all { it ushr 24 == 255 })
        assertTrue((pixels[9 * 32 + 15] and 255) in 80..220)
        assertTrue((pixels[9 * 32 + 16] and 255) in 30..180)
        assertEquals(-1, pixels[9 * 32])
    }
}
