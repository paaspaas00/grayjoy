package com.futo.platformplayer.compose.engine

/** Each authenticated connection owns its attenuation, so stale disconnects cannot reset a new session. */
internal class VoiceVolumeLeases(private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private val levels = mutableMapOf<String, Float>()
    private val expiresAt = mutableMapOf<String, Long>()
    val volume: Float? get() = levels.values.minOrNull()
    fun set(owner: String, percent: Int?): Boolean {
        require(owner.isNotBlank())
        if (percent == null) { expiresAt.remove(owner); return levels.remove(owner) != null }
        require(percent in 0..100)
        expiresAt[owner] = clock() + 60_000L
        val next = percent / 100f
        if (levels[owner] == next) return false
        levels[owner] = next
        return true
    }
    fun expire(): Boolean {
        val now = clock()
        val expired = expiresAt.filterValues { it <= now }.keys.toList()
        expired.forEach { expiresAt.remove(it); levels.remove(it) }
        return expired.isNotEmpty()
    }
    fun clear() { levels.clear(); expiresAt.clear() }
}
