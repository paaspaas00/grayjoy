package com.futo.platformplayer.api.media.platforms.js

data class SourceCaptchaData(
    val cookieMap: HashMap<String, HashMap<String, String>>? = null,
    val headers: Map<String, Map<String, String>> = emptyMap(),
    val userAgent: String? = null,
)
