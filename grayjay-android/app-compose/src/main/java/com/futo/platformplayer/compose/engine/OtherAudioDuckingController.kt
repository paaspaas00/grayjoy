package com.futo.platformplayer.compose.engine

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/**
 * Lowers Grayjoy's own player volume while Android reports another active audio player.
 *
 * Playback configurations are intentionally anonymized for ordinary applications, so the
 * controller cannot identify the competing package. That is desirable for this opt-in feature:
 * notifications, navigation guidance, assistants, and other media all receive the same treatment.
 * The callback is registered only while Grayjoy is playing; no polling or audio capture is used.
 */
internal class OtherAudioDuckingController(
    context: Context,
    private val player: ExoPlayer,
) {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var enabled = false
    private var duckVolume = DEFAULT_DUCK_VOLUME_PERCENT / 100f
    private var callbackRegistered = false
    private var otherAudioActive = false
    private var duckingApplied = false
    private var transitionRunnable: Runnable? = null
    private var rampRunnable: Runnable? = null
    private var rampGeneration = 0
    private var released = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            updateOtherAudioState(
                shouldDuckForActivePlaybackCount(configs.size),
            )
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            runOnMainThread(::updateRegistration)
        }
    }

    init {
        player.addListener(playerListener)
    }

    fun configure(enabled: Boolean, duckVolumePercent: Int) {
        runOnMainThread {
            if (released) return@runOnMainThread
            this.enabled = enabled
            duckVolume = clampDuckVolumePercent(duckVolumePercent) / 100f
            updateRegistration()
            if (duckingApplied) {
                animateVolumeTo(duckVolume, ATTACK_RAMP_MS)
            }
        }
    }

    fun release() {
        runOnMainThread {
            if (released) return@runOnMainThread
            released = true
            player.removeListener(playerListener)
            unregisterPlaybackCallback()
            cancelTransition()
            cancelRamp()
            player.volume = NORMAL_VOLUME
        }
    }

    private fun updateRegistration() {
        if (released) return
        val shouldRegister = enabled && player.isPlaying
        if (shouldRegister && !callbackRegistered) {
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                callbackRegistered = true
                updateOtherAudioState(
                    shouldDuckForActivePlaybackCount(
                        audioManager.activePlaybackConfigurations.size,
                    ),
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to observe other audio playback.", error)
                callbackRegistered = false
            }
        } else if (!shouldRegister && callbackRegistered) {
            unregisterPlaybackCallback()
            resetImmediately()
        } else if (!shouldRegister) {
            resetImmediately()
        }
    }

    private fun unregisterPlaybackCallback() {
        if (!callbackRegistered) return
        runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
            .onFailure { Log.w(TAG, "Unable to unregister audio playback observer.", it) }
        callbackRegistered = false
    }

    private fun updateOtherAudioState(active: Boolean) {
        if (!enabled || !player.isPlaying || released) return
        if (otherAudioActive == active) return
        otherAudioActive = active
        cancelTransition()

        val delayMs = if (active) ATTACK_DEBOUNCE_MS else RELEASE_DEBOUNCE_MS
        val transition = Runnable {
            transitionRunnable = null
            if (!enabled || !player.isPlaying || otherAudioActive != active || released) {
                return@Runnable
            }
            duckingApplied = active
            animateVolumeTo(
                target = if (active) duckVolume else NORMAL_VOLUME,
                durationMs = if (active) ATTACK_RAMP_MS else RELEASE_RAMP_MS,
            )
        }
        transitionRunnable = transition
        handler.postDelayed(transition, delayMs)
    }

    private fun resetImmediately() {
        otherAudioActive = false
        duckingApplied = false
        cancelTransition()
        cancelRamp()
        if (!released) player.volume = NORMAL_VOLUME
    }

    private fun animateVolumeTo(target: Float, durationMs: Long) {
        cancelRamp()
        val start = player.volume
        if (abs(start - target) < MIN_VOLUME_DELTA || durationMs <= 0L) {
            player.volume = target
            return
        }

        val generation = ++rampGeneration
        val startedAt = SystemClock.uptimeMillis()
        val ramp = object : Runnable {
            override fun run() {
                if (released || generation != rampGeneration) return
                val elapsed = SystemClock.uptimeMillis() - startedAt
                val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                // Smoothstep has no abrupt slope at either end of the transition.
                val eased = progress * progress * (3f - 2f * progress)
                player.volume = start + (target - start) * eased
                if (progress < 1f) {
                    handler.postDelayed(this, RAMP_FRAME_MS)
                } else {
                    rampRunnable = null
                }
            }
        }
        rampRunnable = ramp
        handler.post(ramp)
    }

    private fun cancelTransition() {
        transitionRunnable?.let(handler::removeCallbacks)
        transitionRunnable = null
    }

    private fun cancelRamp() {
        rampGeneration += 1
        rampRunnable?.let(handler::removeCallbacks)
        rampRunnable = null
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    companion object {
        const val DEFAULT_DUCK_VOLUME_PERCENT = 35
        private const val NORMAL_VOLUME = 1f
        private const val MIN_VOLUME_DELTA = 0.001f
        private const val ATTACK_DEBOUNCE_MS = 100L
        private const val ATTACK_RAMP_MS = 260L
        private const val RELEASE_DEBOUNCE_MS = 450L
        private const val RELEASE_RAMP_MS = 600L
        private const val RAMP_FRAME_MS = 16L
        private const val TAG = "OtherAudioDucking"
    }
}

internal fun shouldDuckForActivePlaybackCount(activePlaybackCount: Int): Boolean =
    activePlaybackCount > 1

internal fun clampDuckVolumePercent(percent: Int): Int = percent.coerceIn(10, 80)
