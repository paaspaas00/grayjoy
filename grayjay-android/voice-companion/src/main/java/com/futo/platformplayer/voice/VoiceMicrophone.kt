package com.futo.platformplayer.voice

import android.media.*
import android.media.audiofx.*
import android.os.Build

/** Capture-side call processing only. Never changes AudioManager mode, speakerphone or Bluetooth routing. */
internal class VoiceMicrophone private constructor(
    val record: AudioRecord,
    val source: Int,
    private val effects: List<AudioEffect>,
    val gainControl: Boolean,
    val noiseSuppression: Boolean,
    val echoCancellation: Boolean,
    val wideField: Boolean,
) : AutoCloseable {
    val description: String get() = (if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) "Profilo vivavoce" else "Profilo compatibile") +
        " · guadagno ${if (gainControl) "automatico" else "adattivo software"} · rumore ${if (noiseSuppression) "ridotto" else "standard"}" +
        " · eco ${if (echoCancellation) "ridotto" else "standard"}"

    override fun close() {
        runCatching { record.stop() }
        effects.forEach { runCatching { it.release() } }
        record.release()
    }

    companion object {
        @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
        fun open(): VoiceMicrophone {
            var failure: Exception? = null
            for (source in listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
                var record: AudioRecord? = null
                val effects = ArrayList<AudioEffect>()
                try {
                    val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum > 0)
                    record = AudioRecord.Builder().setAudioSource(source)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimum * 2, 64_000)).build()
                    check(record.state == AudioRecord.STATE_INITIALIZED)
                    val input = record
                    fun effect(create: () -> AudioEffect?): Boolean = runCatching {
                        create()?.let { value -> effects += value; value.enabled = true; value.enabled } ?: false
                    }.getOrDefault(false)
                    val agc = effect { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(input.audioSessionId) else null }
                    val ns = effect { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(input.audioSessionId) else null }
                    val aec = effect { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(input.audioSessionId) else null }
                    val field = if (Build.VERSION.SDK_INT >= 29) runCatching {
                        input.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
                        input.setPreferredMicrophoneFieldDimension(-1f)
                    }.getOrDefault(false) else false
                    // Validate the source while the microphone foreground service is visible.
                    input.startRecording()
                    check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    input.stop()
                    return VoiceMicrophone(input, source, effects, agc, ns, aec, field)
                } catch (error: SecurityException) {
                    effects.forEach { runCatching { it.release() } }; record?.release(); throw error
                } catch (error: Exception) {
                    failure = error
                    effects.forEach { runCatching { it.release() } }
                    record?.let { runCatching { it.stop() }; it.release() }
                }
            }
            throw IllegalStateException("Microfono non disponibile", failure)
        }
    }
}
