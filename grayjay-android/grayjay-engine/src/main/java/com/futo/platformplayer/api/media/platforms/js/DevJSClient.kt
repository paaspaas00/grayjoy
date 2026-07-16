package com.futo.platformplayer.api.media.platforms.js

import android.content.Context

/** Type marker retained for the legacy runtime's developer-only branches. */
open class DevJSClient(
    context: Context,
    descriptor: SourcePluginDescriptor,
    script: String,
) : JSClient(context, descriptor, null, script) {
    val devID: String = descriptor.config.id
}
