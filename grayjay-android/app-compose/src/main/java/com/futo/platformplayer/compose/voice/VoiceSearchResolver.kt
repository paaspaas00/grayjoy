package com.futo.platformplayer.compose.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// Optional hints must not hold a good search result hostage to a blocking source request.
private val suggestionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val suggestionSlots = Semaphore(2)

internal suspend fun <T> resolveVoiceYoutubeSearch(query: String, search: suspend (String) -> List<T>,
    suggestions: suspend (String) -> List<String>, id: (T) -> String, title: (T) -> String,
    views: (T) -> Long = { 0L }, downloaded: suspend () -> List<T> = { emptyList() }): T? = coroutineScope {
    val local = withContext(Dispatchers.Default) {
        rankedVoiceVideos(query, downloaded(), id, title, views, minimumScore = .86)
    }
    if (local.isNotEmpty()) return@coroutineScope local.first()
    val correction = suggestionScope.async {
        try { withTimeoutOrNull(1_200) { suggestionSlots.withPermit { suggestions(query) } }.orEmpty() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
    }
    try {
        val initial = search(query)
        suspend fun select(items: List<T>): Pair<T?, Double> = withContext(Dispatchers.Default) {
            val matches = VoiceNameMatcher.rank(query, items.take(60).map { VoiceNameCandidate(id(it), title(it)) }, titleWindows = true)
            val best = matches.firstOrNull()
            rankedVoiceVideos(query, items.take(60), id, title, views).firstOrNull() to (best?.score ?: 0.0)
        }
        val first = select(initial)
        if (first.second >= .88) return@coroutineScope first.first
        val hints = withTimeoutOrNull(1_200) { correction.await() }.orEmpty()
        val alternative = withContext(Dispatchers.Default) {
            VoiceNameMatcher.rank(query, hints.distinct().take(20).map { VoiceNameCandidate(it, it) })
                .firstOrNull { it.score >= .68 && VoiceNameMatcher.normalized(it.candidate.title) != VoiceNameMatcher.normalized(query) }
                ?.candidate?.title
        }
        if (alternative == null) return@coroutineScope first.first
        val corrected = try { search(alternative) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
        select((initial + corrected).distinctBy(id)).first
    } finally { correction.cancel() }
}

/** Popularity breaks ties among relevant matches, never promotes an unrelated viral result. */
internal fun <T> rankedVoiceVideos(query: String, videos: List<T>, id: (T) -> String, title: (T) -> String,
    views: (T) -> Long, minimumScore: Double = .68): List<T> {
    fun tokens(value: String) = VoiceNameMatcher.normalized(value).split(' ')
        .filter { it.isNotBlank() && it !in setOf("di", "by", "the", "il", "la") }.toSet()
    val requested = tokens(query)
    val tokenMatches = videos.filter { requested.isNotEmpty() && tokens(title(it)).containsAll(requested) }.mapTo(HashSet(), id)
    val ranked = VoiceNameMatcher.rank(query, videos.map { VoiceNameCandidate(id(it), title(it)) }, titleWindows = true)
        .map { if (it.candidate.id in tokenMatches) it.copy(score = maxOf(it.score, .965)) else it }
        .sortedByDescending { it.score }
    val threshold = maxOf(minimumScore, (ranked.firstOrNull()?.score ?: 1.0) - .12)
    val scores = ranked.filter { it.score >= threshold }.associate { it.candidate.id to it.score }
    return videos.filter { id(it) in scores }.sortedWith(compareByDescending<T> { views(it).coerceAtLeast(0) }
        .thenByDescending { scores[id(it)] ?: 0.0 })
}
