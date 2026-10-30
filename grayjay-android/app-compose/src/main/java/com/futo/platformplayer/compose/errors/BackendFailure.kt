package com.futo.platformplayer.compose.errors

import android.content.Context
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.diagnostics.CrashLogStore
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import com.futo.platformplayer.compose.engine.isPendingLiveFailure

internal enum class BackendFailureKind(val title: Int, val body: Int, val needsAction: Boolean = false) {
    Upcoming(R.string.video_scheduled_title, R.string.live_not_started_message),
    YoutubeCheck(R.string.friendly_youtube_title, R.string.friendly_youtube_body, true),
    Login(R.string.friendly_login_title, R.string.friendly_login_body, true),
    Network(R.string.friendly_network_title, R.string.friendly_network_body),
    Timeout(R.string.friendly_timeout_title, R.string.friendly_timeout_body),
    RateLimit(R.string.friendly_limit_title, R.string.friendly_limit_body),
    Unavailable(R.string.friendly_unavailable_title, R.string.friendly_unavailable_body, true),
    Region(R.string.friendly_region_title, R.string.friendly_region_body, true),
    Format(R.string.friendly_format_title, R.string.friendly_format_body, true),
    Access(R.string.friendly_access_title, R.string.friendly_access_body, true),
    Protected(R.string.friendly_drm_title, R.string.friendly_drm_body, true),
    Storage(R.string.storage_almost_full, R.string.job_stopped_low_storage, true),
    Source(R.string.friendly_source_title, R.string.friendly_source_body),
}

internal fun failureChain(error: Throwable): List<Throwable> {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val pending = java.util.ArrayDeque<Throwable>().apply { add(error) }
    val result = ArrayList<Throwable>()
    while (pending.isNotEmpty() && result.size < 16) {
        val next = pending.removeFirst()
        if (!seen.add(next)) continue
        result += next
        runCatching { next.cause }.getOrNull()?.let(pending::add)
        next.suppressed.take(4).forEach(pending::add)
    }
    return result
}

internal fun classifyBackendFailure(error: Throwable, source: String = ""): BackendFailureKind {
    val chain = failureChain(error)
    val details = chain.joinToString(" ") { it.javaClass.name + " " + runCatching { it.message }.getOrNull().orEmpty().take(4096) }.lowercase(Locale.ROOT)
    val codes = chain.mapNotNull { (it as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode }.toSet() +
        Regex("(?:http|response\\s*code|status(?:[_ ]?code)?|code)\\s*[:=]?\\s*([45][0-9]{2})\\b").findAll(details).map { it.groupValues[1].toInt() }.toSet()
    val youtube = source.equals("youtube", true) || "youtube" in details
    val challenge = listOf("not a bot", "bot check", "botcheck", "login_required", "captcha", "confirm you", "conferma di non", "verificare di nuovo", "nuova verifica").any(details::contains)
    return when {
        error.isPendingLiveFailure() -> BackendFailureKind.Upcoming
        listOf("enospc", "no space left", "insufficientstorage", "not enough free storage").any(details::contains) -> BackendFailureKind.Storage
        youtube && challenge -> BackendFailureKind.YoutubeCheck
        429 in codes || "rate limit" in details || "too many requests" in details -> BackendFailureKind.RateLimit
        listOf("georestrict", "not available in your country", "not available in your region", "geo-block").any(details::contains) -> BackendFailureKind.Region
        401 in codes || listOf("scriptloginrequired", "login required", "login_required", "sign in", "account is unavailable", "account youtube collegato", "unauthorized").any(details::contains) -> BackendFailureKind.Login
        listOf("unknownhostexception", "connectexception", "nointernet", "no internet", "unable to resolve host", "network is unreachable", "network unavailable", "socketexception", "connection reset").any(details::contains) -> BackendFailureKind.Network
        listOf("timeout", "timed out").any(details::contains) -> BackendFailureKind.Timeout
        listOf("drm", "widevine", "license expired", "licence expired").any(details::contains) -> BackendFailureKind.Protected
        listOf("decoder", "unsupported format", "unrecognizedinputformat", "unsupported media").any(details::contains) -> BackendFailureKind.Format
        404 in codes || listOf("video has been removed", "video unavailable", "video is private", "unavailableexception", "not found", "deleted video").any(details::contains) -> BackendFailureKind.Unavailable
        403 in codes || "forbidden" in details || "access denied" in details -> BackendFailureKind.Access
        else -> BackendFailureKind.Source
    }
}

internal fun friendlyBackendMessage(context: Context, error: Throwable, fallback: Int = R.string.friendly_source_body, source: String = "", music: Boolean = false): String {
    fun message(id: Int) = com.futo.platformplayer.compose.ui.musicProfileString(context.resources, id, music)
    val kind = classifyBackendFailure(error, source)
    BackendDiagnostics.failure("request", error, source)
    val localOperationFallbacks = setOf(R.string.profile_split_failed, R.string.backup_read_failed,
        R.string.newpipe_import_failed, R.string.database_import_failed, R.string.export_failed)
    if (fallback in localOperationFallbacks && kind != BackendFailureKind.Storage) return message(fallback)
    if (kind == BackendFailureKind.Source) {
        val knownMessages = listOf(R.string.results_page_expired, R.string.catalog_audio_not_found,
            R.string.source_profile_locked, R.string.library_unreadable_write_blocked,
            R.string.library_recovery_write_blocked, R.string.profile_data_unavailable)
        val original = runCatching { error.message }.getOrNull()
        knownMessages.firstOrNull { context.getString(it) == original }?.let { return message(it) }
    }
    val genericFallbacks = setOf(R.string.home_feed_refresh_failed, R.string.search_failed, R.string.creator_load_failed,
        R.string.video_details_load_failed, R.string.playlist_load_failed, R.string.more_from_source_load_failed,
        R.string.source_could_not_play, R.string.source_install_failed, R.string.trusted_source_install_failed)
    return message(if (kind == BackendFailureKind.Source && fallback !in genericFallbacks) fallback else kind.body)
}

internal object BackendDiagnostics {
    private val reported = java.util.WeakHashMap<Throwable, Long>()
    fun failure(operation: String, error: Throwable, source: String = "") {
        if (error is kotlinx.coroutines.CancellationException) return
        val now = System.nanoTime()
        if (synchronized(reported) { val previous = reported.put(error, now); previous != null && now - previous < 200_000_000L }) return
        CrashLogStore.recordEvent("Backend", "error", error, "Backend request failed", mapOf(
            "operation" to operation, "reason" to classifyBackendFailure(error, source).name,
            "phase" to "failed"))
    }
    fun fallback(operation: String, from: String, to: String, error: Throwable?, phase: String) {
        CrashLogStore.recordEvent("Backend", if (phase == "failed") "error" else "warning", error,
            "Backend fallback", mapOf("operation" to operation, "from" to from, "to" to to,
                "reason" to (error?.let { classifyBackendFailure(it, "youtube").name } ?: "Capability"), "phase" to phase))
    }
}
