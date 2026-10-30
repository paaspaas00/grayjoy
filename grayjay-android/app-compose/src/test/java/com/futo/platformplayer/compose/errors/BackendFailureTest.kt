package com.futo.platformplayer.compose.errors

import com.futo.platformplayer.compose.diagnostics.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class BackendFailureTest {
    @Test fun pendingLiveIsNotPresentedAsADeletedVideoOrBrokenSource() {
        val error = IllegalStateException("No supported stream")
        error.addSuppressed(IllegalArgumentException("LIVE_STREAM_OFFLINE: This live event will begin in a few moments"))
        assertEquals(BackendFailureKind.Upcoming, classifyBackendFailure(error, "youtube"))
    }
    @Test fun theReportedYoutubeChallengeGetsAnActionableDialogWithoutRawText() {
        val error = IllegalStateException("Both YouTube playback engines failed.", IOException("LOGIN_REQUIRED: Sign in to confirm that you are not a bot"))
            .apply { addSuppressed(IllegalStateException("[Youtube] P:Login required (No fallback)")) }
        assertEquals(BackendFailureKind.YoutubeCheck, classifyBackendFailure(error))
        assertTrue(classifyBackendFailure(error).needsAction)
    }
    @Test fun commonBackendProblemsAreClassifiedWithoutAssumingEveryFailureIsALoginProblem() {
        assertEquals(BackendFailureKind.Network, classifyBackendFailure(java.net.UnknownHostException("host")))
        assertEquals(BackendFailureKind.Timeout, classifyBackendFailure(SocketTimeoutException()))
        assertEquals(BackendFailureKind.RateLimit, classifyBackendFailure(IOException("HTTP 429")))
        assertEquals(BackendFailureKind.Access, classifyBackendFailure(IOException("response code: 403")))
        assertEquals(BackendFailureKind.Login, classifyBackendFailure(IOException("statusCode=401")))
        assertEquals(BackendFailureKind.Region, classifyBackendFailure(IOException("Not available in your country")))
        assertEquals(BackendFailureKind.Protected, classifyBackendFailure(IOException("Widevine license expired")))
        assertEquals(BackendFailureKind.Format, classifyBackendFailure(IOException("Decoder init failed")))
        assertEquals(BackendFailureKind.Storage, classifyBackendFailure(IOException("ENOSPC: no space left on device")))
        assertEquals(BackendFailureKind.Source, classifyBackendFailure(IOException("failed for video 429abcd403")))
        assertFalse(classifyBackendFailure(SocketTimeoutException()).needsAction)
    }
    @Test fun cyclesHugeMessagesAndSuppressedErrorsHaveBoundedCost() {
        val first = IOException("x".repeat(100_000)); val second = IllegalStateException("YouTube not a bot LOGIN_REQUIRED")
        first.initCause(second); second.initCause(first)
        repeat(100) { first.addSuppressed(IOException("error")) }
        assertTrue(failureChain(first).size <= 16)
        assertEquals(BackendFailureKind.YoutubeCheck, classifyBackendFailure(first))
    }
    @Test fun privateLogsKeepTheActualReasonButRedactCredentialsAcrossRepresentations() {
        val message = "LOGIN_REQUIRED: Sign in to confirm that you are not a bot"
        assertEquals(message, redactDiagnosticMessage(message))
        val secret = "SENSITIVE_TEST_VALUE"
        for (key in listOf("Cookie", "Set-Cookie", "Authorization", "access_token", "refreshToken", "password", "SAPISID", "__Secure-3PSID", "headers", "cookieMap", "oauth_token", "poToken", "visitorData", "sessionId", "authData")) {
            for (separator in listOf(": ", "=", "': '", "\": \"")) {
                val input = "HTTP 403\n$key$separator$secret; second=$secret\nwrapped=$secret"
                assertFalse("Failed to redact $key / $separator", redactDiagnosticMessage(input).contains(secret))
            }
        }
        val safe = redactDiagnosticMessage("LOGIN_REQUIRED https://private.test/?token=$secret user@example.test Bearer $secret")
        assertTrue(safe.contains("LOGIN_REQUIRED"))
        assertFalse(safe.contains(secret)); assertFalse(safe.contains("user@")); assertFalse(safe.contains("https://"))
        assertTrue(redactDiagnosticMessage("x".repeat(100000)).length <= 3000)
    }
    @Test fun exportedBackendFieldsAreUsefulButCannotCarryArbitraryMessagesOrIdentifiers() {
        val fields = safeBackendEventFields(mapOf("from" to "NewPipe", "to" to "Grayjay", "reason" to "YoutubeCheck",
            "phase" to "attempt", "operation" to "playback", "message" to "SECRET", "account" to "user@example.test"))
        assertEquals(5, fields.size)
        assertEquals("YoutubeCheck", fields["reason"])
        assertTrue(safeBackendEventFields(mapOf("operation" to "private query", "from" to "user@example.test")).isEmpty())
    }
    @Test fun brokenExceptionGettersCannotCrashErrorPresentation() {
        val error = object : RuntimeException() {
            override val message: String get() = throw IllegalStateException("broken getter")
            override val cause: Throwable get() = throw IllegalStateException("broken cause")
        }
        assertEquals(BackendFailureKind.Source, classifyBackendFailure(error))
        assertTrue(privateExceptionMessages(error).isNotBlank())
    }
}
