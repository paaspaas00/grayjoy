package com.futo.platformplayer.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One prepared chunk ahead: synthesis overlaps playback without unbounded audio buffering. */
internal suspend fun <T> playPreparedSpeech(
    parts: List<String>, prepare: suspend (String) -> T, play: suspend (T, Boolean) -> Unit,
) = coroutineScope {
    val channel = Channel<T>(Channel.RENDEZVOUS)
    val producer = launch(Dispatchers.Default) {
        try {
            for (part in parts) { ensureActive(); channel.send(prepare(part)) }
            channel.close()
        } catch (error: Throwable) { channel.close(error); throw error }
    }
    try {
        parts.indices.forEach { index -> play(channel.receive(), index == parts.lastIndex) }
    } finally { producer.cancelAndJoin(); channel.cancel() }
}
