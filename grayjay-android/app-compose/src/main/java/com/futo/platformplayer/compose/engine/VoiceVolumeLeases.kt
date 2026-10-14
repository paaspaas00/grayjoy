package com.futo.platformplayer.compose.engine

/** Each authenticated connection owns its attenuation, so stale disconnects cannot reset a new session. */
internal class VoiceVolumeLeases {
    private val levels = mutableMapOf<String, Float>()
    val volume: Float? get() = levels.values.minOrNull()
    fun set(owner: String, percent: Int?): Boolean {
        require(owner.isNotBlank())
        if (percent == null) return levels.remove(owner) != null
        require(percent in 0..100)
        val next = percent / 100f
        if (levels[owner] == next) return false
        levels[owner] = next
        return true
    }
    fun clear() = levels.clear()
}
