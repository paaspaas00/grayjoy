package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSurfaceLayoutTest {
    @Test
    fun drmPlaybackUsesSurfaceViewLayout() {
        assertEquals(R.layout.view_compose_secure_player, playerViewLayout(isDrmProtected = true))
    }

    @Test
    fun ordinaryPlaybackKeepsMorphableTextureViewLayout() {
        assertEquals(R.layout.view_compose_player, playerViewLayout(isDrmProtected = false))
    }
}
