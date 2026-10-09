package com.futo.platformplayer.compose.engine

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.futo.platformplayer.compose.shorts.BoundedMediaPrefixCache
import com.futo.platformplayer.compose.shorts.PrefixCacheHttpDataSource
import com.futo.platformplayer.compose.shorts.initialShortsMediaUrls
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ShortsPrefixCacheTest {
    @Test fun mergedDashWarmsPreferredVideoAndAudioInsteadOfOnlyFirstRepresentation() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT30S" minBufferTime="PT1S">
              <Period duration="PT30S">
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Representation id="low" bandwidth="500000" width="854" height="480" codecs="avc1.4d401e"><BaseURL>https://example.test/video480</BaseURL><SegmentBase indexRange="100-199"><Initialization range="0-99"/></SegmentBase></Representation>
                  <Representation id="high" bandwidth="2000000" width="1920" height="1080" codecs="avc1.4d4028"><BaseURL>https://example.test/video1080</BaseURL><SegmentBase indexRange="100-199"><Initialization range="0-99"/></SegmentBase></Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4" lang="en">
                  <Representation id="english" bandwidth="128000" audioSamplingRate="48000" codecs="mp4a.40.2"><BaseURL>https://example.test/audio-en</BaseURL><SegmentBase indexRange="100-199"><Initialization range="0-99"/></SegmentBase></Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4" lang="it">
                  <Representation id="italian" bandwidth="128000" audioSamplingRate="48000" codecs="mp4a.40.2"><BaseURL>https://example.test/audio-it</BaseURL><SegmentBase indexRange="100-199"><Initialization range="0-99"/></SegmentBase></Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        assertEquals(listOf("https://example.test/video1080", "https://example.test/audio-it"),
            initialShortsMediaUrls("https://example.test/manifest", manifest, "application/dash+xml", 1080, "it-IT"))
        assertEquals(listOf("https://example.test/video480", "https://example.test/audio-en"),
            initialShortsMediaUrls("https://example.test/manifest", manifest, "application/dash+xml", 480, "en"))
    }

    @Test fun playbackActuallyConsumesWarmBytesAndResumesUpstreamAtFirstUncachedByte() = runBlocking {
        val upstream = FakeFactory("abcdefghijklmnopqrst".toByteArray())
        val cache = BoundedMediaPrefixCache(32)
        val factory = PrefixCacheHttpDataSource.Factory(upstream, cache, "profile/video", emptyMap())
        factory.warmPrefix("https://example.test/media", 8, SystemClock.elapsedRealtime() + 60_000)
        assertEquals(8, upstream.bytesRead)
        assertEquals(8, cache.byteCount())
        val player = factory.createDataSource()
        val bytes = ByteArrayOutputStream()
        try {
            player.open(DataSpec.Builder().setUri("https://example.test/media").build())
            val buffer = ByteArray(5)
            while (true) {
                val count = player.read(buffer, 0, buffer.size)
                if (count < 0) break
                bytes.write(buffer, 0, count)
            }
        } finally { player.close() }
        assertEquals("abcdefghijklmnopqrst", bytes.toString("UTF-8"))
        assertEquals(listOf(0L, 8L), upstream.requests.map { it.position })
        assertEquals(20, upstream.bytesRead)
        assertEquals(8, cache.byteCount()) // Playback did not turn speculation into a full download.
    }

    @Test fun cachedInitializationRangeNeedsNoNetworkAndProfileNamespaceCannotReuseIt() = runBlocking {
        val upstream = FakeFactory("abcdefghijklmnopqrst".toByteArray())
        val cache = BoundedMediaPrefixCache(32)
        val first = PrefixCacheHttpDataSource.Factory(upstream, cache, "profile-one/video", emptyMap())
        first.warmPrefix("https://example.test/media", 8, SystemClock.elapsedRealtime() + 60_000)
        first.createDataSource().let { source ->
            try {
                assertEquals(3L, source.open(DataSpec.Builder().setUri("https://example.test/media").setPosition(2).setLength(3).build()))
                val bytes = ByteArray(3)
                assertEquals(3, source.read(bytes, 0, 3))
                assertEquals("cde", bytes.toString(Charsets.UTF_8))
                assertEquals(-1, source.read(bytes, 0, 3))
                assertEquals(1, upstream.requests.size)
            } finally { source.close() }
        }
        val second = PrefixCacheHttpDataSource.Factory(upstream, cache, "profile-two/video", emptyMap())
        second.createDataSource().let { source ->
            try { source.open(DataSpec.Builder().setUri("https://example.test/media").setLength(3).build()) }
            finally { source.close() }
        }
        assertEquals(2, upstream.requests.size)
    }

    private class FakeFactory(private val bytes: ByteArray) : HttpDataSource.Factory {
        val requests = mutableListOf<DataSpec>()
        var bytesRead = 0
        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>) = this
        override fun createDataSource(): HttpDataSource = object : HttpDataSource {
            private var spec: DataSpec? = null
            private var position = 0
            private var end = 0
            override fun open(dataSpec: DataSpec): Long {
                requests += dataSpec; spec = dataSpec; position = dataSpec.position.toInt()
                end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) bytes.size
                    else minOf(bytes.size.toLong(), dataSpec.position + dataSpec.length).toInt()
                return (end - position).toLong()
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= end) return -1
                val count = minOf(length, end - position)
                bytes.copyInto(buffer, offset, position, position + count)
                position += count; bytesRead += count; return count
            }
            override fun close() { spec = null }
            override fun getUri(): Uri? = spec?.uri
            override fun getResponseCode() = 200
            override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun setRequestProperty(name: String, value: String) = Unit
            override fun clearRequestProperty(name: String) = Unit
            override fun clearAllRequestProperties() = Unit
        }
    }
}
