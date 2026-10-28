package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class LegacyKeyboardInsetsTest {
    @Test fun unresizedWindowLiftsPlayerByKeyboardOcclusion() {
        assertEquals(LegacyKeyboardInsets(true, 667), legacyKeyboardInsets(2160, 2160, 1493, 300))
    }
    @Test fun resizedWindowMustNotApplyTheKeyboardTwice() {
        assertEquals(LegacyKeyboardInsets(true, 0), legacyKeyboardInsets(2160, 1493, 1493, 300))
    }
    @Test fun systemNavigationAloneIsNotTheKeyboard() {
        assertEquals(LegacyKeyboardInsets(), legacyKeyboardInsets(2160, 2160, 2016, 300))
    }
}
