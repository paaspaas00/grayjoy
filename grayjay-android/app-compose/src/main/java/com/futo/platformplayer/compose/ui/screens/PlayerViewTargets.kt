package com.futo.platformplayer.compose.ui.screens

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** One UI target per player across Now Playing, fullscreen and PiP. Main-thread only. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object PlayerViewTargets {
    private val targets = WeakHashMap<Player, WeakReference<PlayerView>>()

    fun attach(view: PlayerView, player: Player) {
        if (view.player != null && view.player !== player) detach(view)
        val previous = targets[player]?.get()
        if (previous === view && view.player === player) return
        // Media3 attaches the new surface before releasing the old target. An old PiP
        // view must not retain the player and later reclaim its video output.
        PlayerView.switchTargetView(player, previous?.takeUnless { it === view }, view)
        targets[player] = WeakReference(view)
    }

    fun detach(view: PlayerView) {
        val player = view.player
        if (player != null && targets[player]?.get() === view) targets.remove(player)
        view.player = null
    }
}
