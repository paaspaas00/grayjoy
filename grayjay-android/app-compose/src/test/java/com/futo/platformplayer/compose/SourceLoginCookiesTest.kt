package com.futo.platformplayer.compose

import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import org.junit.Assert.*
import org.junit.Test

class SourceLoginCookiesTest {
    @Test fun absentProfileNeverSeedsAnotherProfilesCookies() {
        assertTrue(sourceLoginCookieSeeds(null).isEmpty())
        val auth = SourceAuth(cookieMap = hashMapOf(".example.test" to hashMapOf("session" to "my-token")))
        assertEquals(listOf("https://example.test/" to
            "session=my-token; Domain=example.test; Path=/; Secure"), sourceLoginCookieSeeds(auth))
    }

    @Test fun domainAndCookieAttributesCannotBeInjected() {
        val domains = listOf("", "host/path", "host:123", "user@host", "host?x", "host#x", "host\n.test")
        for (domain in domains) {
            assertTrue(domain, sourceLoginCookieSeeds(SourceAuth(cookieMap = hashMapOf(
                domain to hashMapOf("session" to "token"),
            ))).isEmpty())
        }
        val auth = SourceAuth(cookieMap = hashMapOf("example.test" to hashMapOf(
            "bad=name" to "token", "bad\nname" to "token", "session" to "token; Domain=other.test",
            "other" to "token\r\n", "__Host-session" to "safe",
        )))
        assertEquals(listOf("https://example.test/" to "__Host-session=safe; Path=/; Secure"),
            sourceLoginCookieSeeds(auth))
    }
}
