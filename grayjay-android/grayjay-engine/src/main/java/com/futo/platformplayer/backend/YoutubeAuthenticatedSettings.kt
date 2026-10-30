package com.futo.platformplayer.backend

/** Request-scoped overrides; never persist changes to the linked account's plugin preferences. */
internal fun youtubeAuthenticatedSettings(settings: Map<String, String?>, useLogin: Boolean): HashMap<String, String?> =
    HashMap(settings).apply {
        if (useLogin) {
            put("authDetails", "true")
            put("allowLoginFallback", "true")
            // Read/play requests need authentication, not an additional watch-history tracker.
            put("youtubeActivity", "false")
            remove("composeLegacyAgeFallback")
        }
    }

/** Keep the search context and HTTP client on the same signed-in session as playback. */
internal fun String.withAuthenticatedYoutubeSearch(): String = this + "\n" + AUTHENTICATED_YOUTUBE_SEARCH

internal val AUTHENTICATED_YOUTUBE_SEARCH = """
;(function () {
    if (typeof requestSearch === "function") {
        const originalSearch = requestSearch;
        requestSearch = function (query, useAuth, params) {
            return originalSearch(query, bridge.isLoggedIn() ? true : useAuth, params);
        };
    }
    if (typeof requestSearchContinuation === "function") {
        const originalContinuation = requestSearchContinuation;
        requestSearchContinuation = function (continuation, useAuth) {
            return originalContinuation(continuation, bridge.isLoggedIn() ? true : useAuth);
        };
    }
})();
""".trimIndent()
