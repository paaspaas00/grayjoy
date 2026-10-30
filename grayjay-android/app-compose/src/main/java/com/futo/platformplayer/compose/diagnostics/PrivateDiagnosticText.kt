package com.futo.platformplayer.compose.diagnostics

import com.futo.platformplayer.compose.errors.failureChain

private val headerSecrets = Regex("""(?is)\b(?:cookie(?:[-_]?map|[-_]?header)?|set-cookie|authorization|proxy-authorization|auth[-_]?data|credentials|oauth[-_]?token|po[-_]?token|visitor[-_]?data|session[-_]?id|access[-_]?token|refresh[-_]?token|id[-_]?token|token|password|passwd|secret|api[-_]?key|SAPISID|SID|HSID|SSID|SIDCC|__Secure-[A-Za-z0-9_-]+|request[-_ ]?body|response[-_ ]?body|headers)["']?\s*[:=]\s*.*""")
private val authTokens = Regex("(?i)\\b(?:Bearer|Basic|SAPISID(?:1P|3P)?HASH)\\s+[^\\s,;]+")
private val urls = Regex("(?i)(?:https?|file|content)://[^\\s\\\"'<>]+")
private val emails = Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val opaqueTokens = Regex("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{48,}(?![A-Za-z0-9])")
private val jwtTokens = Regex("""[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")

/** Private diagnostics retain the actual failure wording, but never authentication material. */
internal fun redactDiagnosticMessage(value: String?, limit: Int = 3000): String {
    var text = value.orEmpty().take(12_000)
    for (pattern in listOf(headerSecrets, authTokens, urls, emails, jwtTokens, opaqueTokens)) {
        text = pattern.replace(text, "[redacted]")
    }
    return text.filter { it == '\n' || it == '\t' || !it.isISOControl() }.take(limit)
}

internal fun privateExceptionMessages(error: Throwable): String = failureChain(error).joinToString("\n") {
    it.javaClass.name + ": " + redactDiagnosticMessage(runCatching { it.message }.getOrNull(), 800)
}.take(4000)

internal fun safeBackendEventFields(fields: Map<String, String>): Map<String, String> {
    val allowed = mapOf(
        "from" to setOf("NewPipe", "Grayjay"), "to" to setOf("NewPipe", "Grayjay"),
        "phase" to setOf("attempt", "recovered", "failed", "cancelled", "primary_recovered"),
        "reason" to (com.futo.platformplayer.compose.errors.BackendFailureKind.entries.map { it.name }.toSet() + "Capability"),
        "operation" to setOf("request", "playback", "search", "suggestions", "home", "subscriptions", "channel", "channel search",
            "playlist", "comments", "replies", "storyboard", "download", "music lookup", "route", "video", "extras", "channel tab", "url", "import"),
    )
    return fields.filter { (key, value) -> value in allowed[key].orEmpty() }
}
