package com.futo.platformplayer.compose.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

internal data class RecognizedSong(val title: String, val artist: String, val artwork: String) {
    val query get() = "$artist $title".trim()
}
internal fun parseRecognizedSong(json: JSONObject): RecognizedSong? {
    val track = json.optJSONObject("track") ?: return null
    val title = track.optString("title").trim().take(300)
    if (title.isBlank()) return null
    val image = track.optJSONObject("images")?.let { it.optString("coverarthq").ifBlank { it.optString("coverart") } }.orEmpty()
    return RecognizedSong(title, track.optString("subtitle").take(300), image.takeIf { it.startsWith("https://") }.orEmpty())
}
internal class SongRecognitionClient {
    companion object { private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).build()
    }

    @SuppressLint("MissingPermission")
    suspend fun recognize(onLevel: (RecognitionAudioFrame) -> Unit, onSearching: () -> Unit): RecognizedSong? = withContext(Dispatchers.IO) {
        repeat(2) {
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0) { "Microphone format unavailable" }
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
            val fingerprint = SongFingerprint()
            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                recorder.startRecording()
                val chunk = ShortArray(512)
                var filled = 0; var total = 0
                while (total < 16000 * 6) {
                    currentCoroutineContext().ensureActive()
                    val n = recorder.read(chunk, filled, chunk.size - filled)
                    check(n > 0) { "Microphone read failed ($n)" }
                    filled += n
                    if (filled == chunk.size) {
                        val rms = sqrt(chunk.sumOf { it.toDouble() * it } / chunk.size)
                        fingerprint.feed(chunk)
                        onLevel(RecognitionAudioFrame(recognitionDisplayLevel(rms), fingerprint.visualSpectrum()))
                        total += filled; filled = 0
                    }
                }
            } finally {
                runCatching { recorder.stop() }; recorder.release(); onLevel(RecognitionAudioFrame.Silence)
            }
            currentCoroutineContext().ensureActive()
            onSearching()
            lookup(fingerprint)?.let { return@withContext it }
        }
        null
    }

    internal suspend fun lookup(fingerprint: SongFingerprint): RecognizedSong? = withContext(Dispatchers.IO) {
            val time = System.currentTimeMillis()
            val data = JSONObject().put("signature", JSONObject().put("samplems", fingerprint.durationMs)
                .put("timestamp", time).put("uri", fingerprint.uri())).put("timestamp", time)
                .put("timezone", java.util.TimeZone.getDefault().id)
            val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/${UUID.randomUUID()}/${UUID.randomUUID()}?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"
            val request = Request.Builder().url(url).header("User-Agent", "Shazam/14.1.0 (Android)")
                .post(data.toString().toRequestBody("application/json".toMediaType())).build()
            val json = suspendCancellableCoroutine<JSONObject> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            try {
                                check(it.isSuccessful) { "Recognition HTTP ${it.code}" }
                                val body = it.body ?: error("Empty recognition response")
                                val bytes = body.byteStream().use { stream ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (output.size() <= 1_000_000) {
                                        val n = stream.read(buffer, 0, minOf(buffer.size, 1_000_001 - output.size()))
                                        if (n < 0) break
                                        output.write(buffer, 0, n)
                                    }
                                    output.toByteArray()
                                }
                                check(bytes.size <= 1_000_000) { "Recognition response too large" }
                                if (continuation.isActive) continuation.resume(JSONObject(String(bytes, Charsets.UTF_8)))
                            } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                        }
                    }
                })
            }
            parseRecognizedSong(json)
    }
}
