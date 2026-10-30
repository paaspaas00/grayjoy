package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.ChannelUiModel
import java.net.URI

internal fun channelPageLinks(channel: ChannelUiModel): Map<String, String> {
    if (!channel.sourceId.equals("spotify", true)) return channel.links
    return channel.links.filterNot { (label, url) ->
        val host = runCatching { URI(url).host.orEmpty().lowercase(java.util.Locale.ROOT) }.getOrDefault("")
        label.equals("spotify", true) || host == "spotify.com" || host.endsWith(".spotify.com")
    }
}
