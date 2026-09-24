package com.futo.platformplayer.compose

import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import java.net.URI

/** Restore the selected profile's web session without accepting cookie attribute injection. */
internal fun sourceLoginCookieSeeds(auth: SourceAuth?): List<Pair<String, String>> =
    auth?.cookieMap.orEmpty().flatMap { (cookieDomain, values) ->
        val domain = cookieDomain.trimStart('.').lowercase()
        val validDomain = runCatching {
            domain.isNotBlank() && URI("https://$domain/").let {
                it.host == domain && it.port == -1 && it.rawUserInfo == null &&
                    it.rawPath == "/" && it.rawQuery == null && it.rawFragment == null
            }
        }.getOrDefault(false)
        if (!validDomain) emptyList() else values.mapNotNull { (name, value) ->
            if (name.isBlank() || name.any { it <= ' ' || it in "()<>@,;:\\\"/[]?={}\u007f" } ||
                value.any { it < ' ' || it == ';' || it == '\u007f' }
            ) null else {
                val domainAttribute = if (name.startsWith("__Host-")) "" else "; Domain=$domain"
                "https://$domain/" to "$name=$value$domainAttribute; Path=/; Secure"
            }
        }
    }
