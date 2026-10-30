package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.*
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.*
import org.junit.Assert.*
import java.util.Locale
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry

class SpotifyTerminologyUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var player: ExoPlayer? = null
    @After fun cleanup() { rule.runOnUiThread { player?.release() } }
    private fun italianContext(): Context {
        val config = Configuration(rule.activity.resources.configuration).apply { setLocale(Locale.ITALIAN) }
        return rule.activity.createConfigurationContext(config)
    }
    @Test fun artistPageSearchSettingsAndSpeedUseArtistAndTrackLabelsWithoutChangingTitles() {
        val context = rule.activity
        val channel = ChannelUiModel("artist", "Radio Channel", "spotify", "Spotify", 0, "", "")
        val video = VideoUiModel("song", "Video Killed the Radio Star", "Radio Channel", "", "3:00", sourceId = "spotify")
        rule.setContent { CompositionLocalProvider(LocalMusicPresentation provides true) { GrayjayTheme(dynamicColor = false, musicProfile = true) {
            Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                ChannelDetailScreen(channel, ChannelDetailUiState(channelId = channel.id, channel = channel, videos = listOf(video), isLoaded = true),
                    false, {}, onTabSelected = {}, onPlaylistClick = {}, onLoadMore = {}, onVideoClick = {}, onVideoLongClick = {})
            }
        } } }
        rule.onNodeWithTag("channel-detail-artist").performScrollToNode(hasTestTag("channel-video-search"))
        val searchLabel = musicProfileString(context.resources, R.string.search_channel_videos, true)
        rule.onNode(hasText(searchLabel) or hasContentDescription(searchLabel)).assertExists()
        rule.onAllNodesWithText(context.getString(R.string.music_tracks)).onFirst().assertExists()
        rule.onNodeWithText(video.title).assertExists()
        rule.onNodeWithText("Video").assertDoesNotExist()
        rule.onNodeWithTag("channel-detail-artist").performScrollToIndex(0)
        rule.onNodeWithContentDescription(musicProfileString(context.resources, R.string.channel_settings, true)).performClick()
        rule.onNodeWithText(musicProfileString(context.resources, R.string.channel_settings, true)).assertExists()
        rule.onNodeWithText(musicProfileString(context.resources, R.string.channel_playback_speed, true)).assertExists()
        rule.onNodeWithText(context.getString(R.string.channel_playback_speed)).assertDoesNotExist()
        capture("spotify-artist-terminology")
    }
    @Test fun preferencesCanBeFoundByArtistAndKeepYoutubeTerminologyUnchanged() {
        val context = rule.activity
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        var state by mutableStateOf(GrayjayUiState(sources = listOf(SourceUiModel("spotify", "Spotify", "", 0L, true))))
        rule.setContent {
            GrayjayTheme(dynamicColor = false) { GrayjayApp(state, player!!, GrayjayAppActions()) }
        }
        rule.onNodeWithTag("nav-settings").performClick()
        rule.onNodeWithTag("settings-search").performTextInput(context.getString(R.string.music_artist))
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("per-channel-playback-speed"))
        rule.onNodeWithText(musicProfileString(context.resources, R.string.per_channel_playback_speed, true)).assertExists()
        rule.onNodeWithText(context.getString(R.string.per_channel_playback_speed)).assertDoesNotExist()
        capture("spotify-preferences-terminology")
        rule.runOnIdle { state = state.copy(sources = listOf(SourceUiModel("youtube", "YouTube", "", 0L, true))) }
        assertEquals(context.getString(R.string.channel_settings), musicProfileString(context.resources, R.string.channel_settings, false))
    }
    @Test fun singularPluralAndFormattedUserValuesAreSafeInBothProfiles() {
        val r = italianContext().resources
        assertEquals(r.getString(R.string.open_item), musicProfileString(r, R.string.open_item, false))
        assertEquals("1 brano", musicProfileQuantity(r, R.plurals.video_count, 1, true, 1))
        assertEquals("2 brani", musicProfileQuantity(r, R.plurals.video_count, 2, true, 2))
        assertEquals("1 artista seguito", musicProfileQuantity(r, R.plurals.followed_creators, 1, true, 1))
        assertEquals(r.getQuantityString(R.plurals.video_count, 2, 2), musicProfileQuantity(r, R.plurals.video_count, 2, false, 2))
        val query = "Video del canale"
        val message = musicProfileString(r, R.string.no_channel_videos_match, true, query)
        assertTrue(message.contains(query))
        assertTrue(Regex("\\bbran[oi]\\b").containsMatchIn(message))
        assertTrue(message.contains("artista"))
    }
    @Test fun allSupportedTranslationsHaveArtistSettingsWhileNonMusicResourcesRemainIdentical() {
        for (tag in listOf("en", "it", "de", "es", "fr", "pt", "pl", "ru", "tr", "ar", "ja", "ko", "zh")) {
            val config = Configuration(rule.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val r = rule.activity.createConfigurationContext(config).resources
            val original = r.getString(R.string.channel_settings)
            val music = musicProfileString(r, R.string.channel_settings, true)
            assertNotEquals(tag, original, music)
            assertEquals(tag, original, musicProfileString(r, R.string.channel_settings, false))
        }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
