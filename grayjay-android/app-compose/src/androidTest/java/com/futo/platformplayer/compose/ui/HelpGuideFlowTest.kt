package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.onboarding.HelpTopic
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI-only fixtures; the user's playlists, subscriptions and downloads are never changed. */
class HelpGuideFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val video = VideoUiModel("guide-song", "Song", "Artist", "", "3:00", sourceId = "youtube",
        authorUrl = "https://www.youtube.com/channel/guide-artist")
    private val local = PlaylistUiModel("guide-local", "My songs", "", listOf(video.id))
    private val album = PlaylistUiModel("guide-album", "An album", "", listOf(video.id), sourceId = "youtube")
    private var state by mutableStateOf(GrayjayUiState())
    private var sideEffects = 0

    private fun runGuide(topic: HelpTopic, body: () -> Unit) {
        lateinit var player: ExoPlayer
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        state = GrayjayUiState(videos = listOf(video), playlists = listOf(local),
            sources = listOf(SourceUiModel("youtube", "YouTube", "", 0, true)),
            search = SearchUiState(query = "Artist", hasSearched = true, videos = listOf(video), playlists = listOf(album),
                channels = listOf(ChannelUiModel(video.authorUrl, "Artist", "youtube", "", 0, "", "", ""))))
        val actions = GrayjayAppActions().apply {
            onOpenVideo = { state = state.copy(nowPlaying = NowPlayingUiState(video = video),
                playback = PlaybackUiState(currentVideoId = video.id)) }
            onLoadChannel = { channel -> state = state.copy(channelDetail =
                ChannelDetailUiState(channelId = channel.id, channel = channel, videos = listOf(video), isLoaded = true)) }
            onLoadRemotePlaylist = { playlist -> state = state.copy(remotePlaylistDetail =
                RemotePlaylistDetailUiState(playlist = playlist, videos = listOf(video))) }
            onPlaylistShuffleChange = { id, enabled -> state = state.copy(shuffledPlaylistIds = if (enabled) setOf(id) else emptySet()) }
            onPlayPlaylist = { sideEffects++ }
            onPlayRemotePlaylist = { sideEffects++ }
            onToggleDownloaded = { sideEffects++ }
            onToggleAudioDownloaded = { sideEffects++ }
        }
        try {
            rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player, actions) } }
            rule.onNodeWithTag("open-help").performClick()
            rule.onNodeWithTag("help-topics").performScrollToNode(hasTestTag("help-topic-${topic.name}"))
            rule.onNodeWithTag("help-topic-${topic.name}").performClick()
            rule.onNodeWithTag("help-topics").performScrollToNode(hasTestTag("help-start-${topic.name}"))
            rule.onNodeWithTag("help-start-${topic.name}").performClick()
            body()
            rule.onNodeWithTag("help-next").assertIsEnabled().performClick()
            rule.onNodeWithTag("help-bubble").assertDoesNotExist()
            rule.runOnIdle { assertEquals(0, sideEffects) }
        } finally { rule.runOnUiThread { player.release() } }
    }
    private fun next() = rule.onNodeWithTag("help-next").assertIsEnabled().performClick()
    private fun openVideo() { next(); rule.onNodeWithTag("video-card-${video.id}").performClick(); next() }

    @Test fun musicGuideReachesRealPlayer() = runGuide(HelpTopic.Music) {
        openVideo()
        rule.onNodeWithTag("toggle-download").assertExists()
    }
    @Test fun downloadGuideReachesAudioVideoChoicesWithoutDownloading() = runGuide(HelpTopic.Download) {
        openVideo()
        rule.onNodeWithTag("toggle-download").performClick()
        rule.onNodeWithTag("download-format-audio").assertIsDisplayed()
        rule.onNodeWithTag("download-format-video").assertIsDisplayed()
    }
    @Test fun followGuideOpensTheChannelWithoutSubscribing() = runGuide(HelpTopic.Follow) {
        rule.onNodeWithTag("search-filter-creators").performClick()
        rule.onNodeWithTag("channel-${video.authorUrl}").performClick()
        rule.onNodeWithTag("channel-follow").assertIsDisplayed()
    }
    @Test fun albumGuideSearchesPlaylistsThenOpensTheAlbum() = runGuide(HelpTopic.Album) {
        next()
        rule.onNodeWithTag("search-filter-playlists").performClick()
        next()
        rule.onNodeWithTag("playlist-${album.id}").performClick()
        next()
        rule.onNodeWithTag("remote-playlist-play-all").assertIsDisplayed()
    }
    @Test fun shuffleGuideWaitsForShuffleThenPointsAtPlayAll() = runGuide(HelpTopic.Random) {
        rule.onNodeWithTag("playlist-${local.id}").performClick()
        next()
        rule.onNodeWithTag("help-next").assertIsNotEnabled()
        rule.onNodeWithTag("shuffle-playlist-${local.id}").performClick()
        next()
        rule.onNodeWithTag("playlist-play-all").assertIsDisplayed()
    }
}
