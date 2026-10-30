package com.futo.platformplayer.compose.engine

import android.content.Context
import com.futo.platformplayer.backend.GrayjayPluginBackend
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.data.YoutubePlaybackAccounts
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal fun isYoutubeLoginChallenge(error: Throwable): Boolean {
    val pending = java.util.ArrayDeque<Throwable>().apply { add(error) }
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    while (pending.isNotEmpty() && seen.size < 12) {
        val current = pending.removeFirst()
        if (!seen.add(current)) continue
        val message = current.message.orEmpty().take(4096).lowercase(java.util.Locale.ROOT)
        if (message.contains("login_required") || message.contains("login required") || message.contains("not a bot")) return true
        current.cause?.let(pending::add)
        current.suppressed.take(4).forEach(pending::add)
    }
    return false
}

/** A separate credentialed runtime keeps a linked session out of the consumer's other plugins. */
internal class AuthenticatedYoutubePlayback(
    context: Context,
    private val activeProfile: () -> String,
    private val disposalScope: CoroutineScope,
    private val createBackend: () -> GrayjayPluginBackend,
) {
    private val app = context.applicationContext
    private val accounts = YoutubePlaybackAccounts(app)
    private val creation = Mutex()
    private val stateLock = Any()
    private val generation = AtomicLong()
    @Volatile private var closed = false
    private val retired = java.util.concurrent.ConcurrentLinkedQueue<GrayjayPluginBackend>()
    private class Slot(val session: YoutubePlaybackAccounts.Session, val backend: GrayjayPluginBackend)
    private var slot: Slot? = null

    suspend fun hasSession(): Boolean = withContext(Dispatchers.IO) {
        if (closed) throw CancellationException("Playback session closed")
        val profile = activeProfile()
        val session = accounts.session(profile)
        if (session == null && accounts.linkedProfile(profile) != null) {
            invalidate()
            throw IOException(app.getString(R.string.youtube_playback_account_unavailable))
        }
        session != null
    }

    suspend fun <T> request(anonymous: () -> GrayjayPluginBackend, block: suspend (GrayjayPluginBackend) -> T): T = withContext(Dispatchers.IO) {
        if (closed) throw CancellationException("Playback session closed")
        val profile = activeProfile()
        val epoch = generation.get()
        val session = accounts.session(profile)
        if (session == null) {
            if (accounts.linkedProfile(profile) != null) throw IOException(app.getString(R.string.youtube_playback_account_unavailable))
            fun checkAnonymous() {
                if (closed || epoch != generation.get() || profile != activeProfile()) throw CancellationException("Playback account changed")
            }
            val value = try { block(anonymous()) } catch (error: Throwable) { checkAnonymous(); throw error }
            checkAnonymous()
            return@withContext value
        }
        val backend = creation.withLock {
            checkCurrent(profile, epoch, session)
            currentCoroutineContext().ensureActive()
            synchronized(stateLock) { slot?.takeIf { it.session.sameAs(session) }?.backend }?.let { return@withLock it }
            val next = createBackend().apply { setProfile(session.owner) }
            val old = synchronized(stateLock) {
                if (closed || epoch != generation.get() || profile != activeProfile()) {
                    retire(next)
                    throw CancellationException("Playback account changed")
                }
                slot.also { slot = Slot(session, next) }
            }
            old?.let { retire(it.backend) }
            next
        }
        currentCoroutineContext().ensureActive()
        val result = try { block(backend) } catch (error: Throwable) { checkCurrent(profile, epoch, session); throw error }
        checkCurrent(profile, epoch, session)
        result
    }

    private fun checkCurrent(profile: String, epoch: Long, session: YoutubePlaybackAccounts.Session) {
        if (closed || epoch != generation.get() || profile != activeProfile() || !session.sameAs(accounts.session(profile))) {
            invalidate()
            throw CancellationException("Playback account changed")
        }
    }

    fun invalidate() {
        val old = synchronized(stateLock) { generation.incrementAndGet(); slot.also { slot = null } }
        old?.let { retire(it.backend) }
    }

    private fun retire(backend: GrayjayPluginBackend) {
        retired.add(backend)
        disposalScope.launch(Dispatchers.IO) { try { backend.release() } finally { retired.remove(backend) } }
    }

    fun release() {
        val last = synchronized(stateLock) { closed = true; generation.incrementAndGet(); slot.also { slot = null } }
        last?.backend?.release()
        while (true) { val old = retired.poll() ?: break; old.release() }
    }
}
