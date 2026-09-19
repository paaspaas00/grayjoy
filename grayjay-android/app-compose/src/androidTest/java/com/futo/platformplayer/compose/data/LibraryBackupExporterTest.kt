package com.futo.platformplayer.compose.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBackupExporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val video = VideoUiModel(
        id = "video-1",
        title = "Export test",
        creator = "Creator",
        metadata = "1 day ago",
        duration = "2:00",
        channelId = "https://www.youtube.com/channel/UC1",
        sourceId = "youtube",
        watchProgress = 0.5f,
        isWatchLater = true,
        lastWatchedAt = 1_700_000_000_000L,
        contentUrl = "https://www.youtube.com/watch?v=video-1",
        thumbnailUrl = "https://example.test/video.jpg",
        authorUrl = "https://www.youtube.com/channel/UC1",
        authorThumbnailUrl = "https://example.test/channel.jpg",
    )
    private val channel = ChannelUiModel(
        id = video.authorUrl,
        name = video.creator,
        sourceId = "youtube",
        source = "YouTube",
        unreadCount = 0,
        followerCount = "1 follower",
        description = "Channel",
        thumbnailUrl = video.authorThumbnailUrl,
    )
    private val playlist = PlaylistUiModel(
        id = "playlist-1",
        title = "Playlist",
        description = "Local",
        videoIds = listOf(video.id),
    )

    @Test
    fun grayjayExportRoundTripsThroughImporter() {
        val bytes = export(LibraryExportFormat.Grayjay)
        val parsed = LegacyGrayjayBackupParser.parse(bytes)

        assertEquals(listOf(channel.id), parsed.subscriptionUrls)
        assertEquals(listOf(video.contentUrl), parsed.watchLaterUrls)
        assertEquals("Playlist", parsed.playlists.single().title)
        assertEquals(video.contentUrl, parsed.history.single().url)
    }

    @Test
    fun newPipeExportRoundTripsThroughImporter() {
        val bytes = export(LibraryExportFormat.NewPipe)
        val parsed = NewPipeBackupParser.parse(bytes, context.cacheDir)

        assertEquals(channel.id, parsed.subscriptions.single().url)
        assertEquals(video.contentUrl, parsed.streams.values.single().url)
        assertEquals("Playlist", parsed.playlists.single().name)
        assertTrue(parsed.history.isNotEmpty())
    }

    private fun export(format: LibraryExportFormat): ByteArray =
        ByteArrayOutputStream().use { output ->
            LibraryBackupExporter(context).export(
                format = format,
                videos = listOf(video),
                playlists = listOf(playlist),
                channels = listOf(channel),
                output = output,
            )
            output.toByteArray()
        }
}
