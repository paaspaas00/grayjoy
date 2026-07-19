package com.futo.platformplayer.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.ui.GrayjayApp
import com.futo.platformplayer.compose.ui.GrayjayUiState
import com.futo.platformplayer.compose.ui.DatabaseImportPreviewUiModel
import com.futo.platformplayer.compose.ui.DatabaseImportSelection
import com.futo.platformplayer.compose.ui.DatabaseImportUiState
import com.futo.platformplayer.compose.ui.DatabaseImportFormat
import com.futo.platformplayer.compose.ui.NowPlayingUiState
import com.futo.platformplayer.compose.ui.PlaybackUiState
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.SearchUiState
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.SourceTrustRequestUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GrayjayAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExoPlayer
    private lateinit var state: MutableState<GrayjayUiState>
    private var installedSourceUrl: String? = null
    private var importPickerRequested = false
    private var newPipeImportPickerRequested = false
    private var confirmedImport: DatabaseImportSelection? = null
    private var trustedUnverifiedSource = false
    private var rejectedUnverifiedSource = false

    @Before
    fun setUp() {
        composeRule.runOnUiThread {
            player = ExoPlayer.Builder(composeRule.activity).build()
        }
        state = mutableStateOf(testState())
        composeRule.setContent {
            GrayjayTheme(dynamicColor = false) {
                GrayjayApp(
                    uiState = state.value,
                    player = player,
                    onDynamicColorsChange = {},
                    onPrivateSessionChange = { enabled ->
                        state.value = state.value.copy(privateSessionEnabled = enabled)
                    },
                    onOpenVideo = ::openVideo,
                    onLoadChannel = {},
                    onHomeFeedSelected = {},
                    onRefreshHome = {},
                    onPlayQueue = { ids -> ids.firstOrNull()?.let(::openVideo) },
                    onPlayPlaylist = {},
                    onPlayPlaylistFrom = { _, _ -> },
                    onTogglePlayback = {},
                    onSkipToNext = {},
                    onSkipToPrevious = {},
                    onSeekPlaybackBy = {},
                    onPlaybackSpeedChange = {},
                    onVideoQualityChange = {},
                    onCaptionsEnabledChange = {},
                    onSubtitleLanguageChange = {},
                    onRetryPlayback = {},
                    onClosePlayback = {
                        state.value = state.value.copy(
                            playback = PlaybackUiState(),
                            nowPlaying = NowPlayingUiState(),
                        )
                    },
                    onToggleWatchLater = { id -> updateVideo(id) { it.copy(isWatchLater = !it.isWatchLater) } },
                    onToggleDownloaded = { id -> updateVideo(id) { it.copy(isDownloaded = !it.isDownloaded) } },
                    onToggleAudioDownloaded = {},
                    onDownloadVideo = { _, _ -> },
                    onDownloadAudio = { _, _ -> },
                    onDownloadVideos = { _, _ -> },
                    onDownloadPlaylist = { _, _ -> },
                    onToggleLiked = { id -> updateVideo(id) { it.copy(isLiked = !it.isLiked) } },
                    onCreatePlaylist = { title, ids -> createPlaylist(title, ids) },
                    onRenamePlaylist = { _, _ -> },
                    onAddVideosToPlaylist = { playlistId, ids -> addToPlaylist(playlistId, ids) },
                    onRemoveVideosFromPlaylist = { _, _ -> },
                    onReorderPlaylist = { _, _ -> },
                    onRemoveVideosFromHistory = {},
                    onSeekPlayback = {},
                    onSourceEnabledChange = { id, enabled ->
                        state.value = state.value.copy(
                            sources = state.value.sources.map {
                                if (it.id == id) it.copy(isEnabled = enabled) else it
                            },
                        )
                    },
                    onInstallSource = { installedSourceUrl = it },
                    onScanSourceQr = {},
                    onRefreshSource = {},
                    onClearSourceCache = {},
                    onRemoveSource = {},
                    onLoginSource = {},
                    onLogoutSource = {},
                    onSearchQueryChange = { query ->
                        state.value = state.value.copy(search = state.value.search.copy(query = query))
                    },
                    onSearchSubmit = { query, _, _ ->
                        state.value = state.value.copy(
                            search = SearchUiState(
                                query = query,
                                hasSearched = true,
                                videos = state.value.videos,
                            ),
                        )
                    },
                    onToggleFollowing = {},
                    onCreatorFollowedChange = { _, _ -> },
                    onChooseDatabaseImport = { importPickerRequested = true },
                    onChooseNewPipeImport = { newPipeImportPickerRequested = true },
                    onRetryDatabaseImport = {},
                    onConfirmDatabaseImport = { confirmedImport = it },
                    onDismissDatabaseImport = {},
                    onTrustUnverifiedSource = {
                        trustedUnverifiedSource = true
                        state.value = state.value.copy(sourceTrustRequest = null)
                    },
                    onRejectUnverifiedSource = {
                        rejectedUnverifiedSource = true
                        state.value = state.value.copy(sourceTrustRequest = null)
                    },
                    onSwitchProfile = {},
                    onCreateProfile = { _, _ -> },
                    onVerifyProfilePin = { _, _ -> true },
                    onDefaultPlaybackSpeedChange = {},
                    onPreferredVideoQualityChange = {},
                    onPreferredAudioBitrateChange = {},
                    onStickyCaptionsChange = {},
                    onShowRecommendationsChange = {},
                    onSearchHistoryChange = {},
                    onKeepScreenAwakeChange = {},
                )
            }
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { player.release() }
    }

    @Test
    fun emptyHomeDoesNotRenderDemoOrContinueWatchingContent() {
        state.value = state.value.copy(videos = emptyList())

        composeRule.onNodeWithText("Your home feed is empty", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE WATCHING").assertDoesNotExist()
        composeRule.onNodeWithText("Building a privacy-first media library").assertDoesNotExist()
    }

    @Test
    fun longPressVideoOffersLikeShareAndPlaylistActions() {
        composeRule.onNodeWithTag("video-card-real-video-one").performScrollTo().performTouchInput {
            longClick()
        }

        composeRule.onNodeWithTag("video-action-like").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(state.value.videos.single().isLiked) }
    }

    @Test
    fun historySupportsMultiSelectAndNewPlaylistCreation() {
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("library-filter-history").performClick()
        composeRule.onNodeWithTag("history-select-inline").performScrollTo().performClick()
        composeRule.onNodeWithText("Select all").performClick()
        composeRule.onNodeWithTag("history-add-to-playlist").performClick()
        composeRule.onNodeWithTag("new-playlist-name").performTextInput("Research")
        composeRule.onNodeWithTag("create-playlist").performClick()

        composeRule.runOnIdle {
            assertEquals("Research", state.value.playlists.single().title)
            assertEquals(2, state.value.playlists.single().videoIds.size)
        }
    }

    @Test
    fun sourceManagerShowsOptionsAndAcceptsConfigUrls() {
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("manage-sources").performClick()

        composeRule.onNodeWithText("1 installed in the Grayjay engine").assertIsDisplayed()
        composeRule.onNodeWithTag("source-youtube").assertIsOn().performClick().assertIsOff()
        composeRule.onNodeWithTag("add-source").performClick()
        composeRule.onNodeWithTag("source-url-field")
            .performTextInput("https://plugins.example/Config.json")
        composeRule.onNodeWithTag("install-source").performClick()

        composeRule.runOnIdle {
            assertEquals("https://plugins.example/Config.json", installedSourceUrl)
        }

        composeRule.onNodeWithText("YouTube").performClick()
        composeRule.onNodeWithText("Update or reinstall").assertIsDisplayed()
    }

    @Test
    fun settingsLaunchesDatabasePickerAndPreviewConfirmsSelectedData() {
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("import-grayjay-database").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(importPickerRequested) }

        state.value = state.value.copy(
            databaseImport = DatabaseImportUiState(
                preview = DatabaseImportPreviewUiModel(
                    fileName = "grayjay-export.zip",
                    sourceCount = 2,
                    pluginSettingsCount = 1,
                    subscriptionCount = 4,
                    watchLaterCount = 3,
                    playlistCount = 2,
                    historyCount = 8,
                    hasLegacySettings = true,
                ),
            ),
        )
        composeRule.onNodeWithTag("database-import-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("database-import-confirm").performClick()

        composeRule.runOnIdle {
            assertTrue(confirmedImport?.importSources == true)
            assertTrue(confirmedImport?.importSubscriptions == true)
            assertTrue(confirmedImport?.importHistory == true)
        }
    }

    @Test
    fun settingsLaunchesNewPipePickerAndShowsNewPipePreview() {
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(6)
        composeRule.onNodeWithTag("import-newpipe-database").performClick()
        composeRule.runOnIdle { assertTrue(newPipeImportPickerRequested) }

        state.value = state.value.copy(
            databaseImport = DatabaseImportUiState(
                preview = DatabaseImportPreviewUiModel(
                    fileName = "NewPipeData.zip",
                    sourceCount = 0,
                    pluginSettingsCount = 0,
                    subscriptionCount = 5,
                    watchLaterCount = 0,
                    playlistCount = 2,
                    historyCount = 9,
                    hasLegacySettings = false,
                    format = DatabaseImportFormat.NewPipe,
                ),
                format = DatabaseImportFormat.NewPipe,
            ),
        )
        composeRule.onNodeWithTag("database-import-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("import-sources").assertDoesNotExist()
        composeRule.onNodeWithTag("import-subscriptions").assertIsDisplayed()
    }

    @Test
    fun signatureMismatchRequiresExplicitPublisherTrustOrRejection() {
        val request = SourceTrustRequestUiModel(
            token = "pending-token",
            pluginName = "Community Source",
            publisher = "Example Publisher",
            publisherUrl = "https://publisher.example",
            configUrl = "https://publisher.example/Config.json",
            publicKeyFingerprint = "AA:BB:CC:DD",
        )
        state.value = state.value.copy(sourceTrustRequest = request)

        composeRule.onNodeWithTag("source-signature-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Do you trust this publisher?", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("trust-unverified-source").performClick()
        composeRule.runOnIdle { assertTrue(trustedUnverifiedSource) }

        state.value = state.value.copy(sourceTrustRequest = request.copy(token = "second-token"))
        composeRule.onNodeWithTag("reject-unverified-source").performClick()
        composeRule.runOnIdle { assertTrue(rejectedUnverifiedSource) }
    }

    @Test
    fun searchWaitsForKeyboardConfirmationBeforeShowingResults() {
        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-field").assertIsFocused().performTextInput("real video")
        composeRule.onNodeWithTag("search-filter-everything").assertDoesNotExist()

        composeRule.onNodeWithTag("search-field").performImeAction()

        composeRule.onNodeWithTag("search-filter-everything").assertIsDisplayed()
    }

    @Test
    fun playerCanCollapseAndExpandWithoutDuplicateMiniPlayer() {
        composeRule.onNodeWithTag("video-card-real-video-one").performScrollTo().performClick()
        composeRule.onNodeWithText("Now playing").assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player").assertDoesNotExist()

        composeRule.onNodeWithTag("media-player").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + 1_300f), durationMillis = 700)
        }
        composeRule.onNodeWithTag("mini-player").assertIsDisplayed()

        composeRule.onNodeWithTag("mini-player").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 1_300f), durationMillis = 700)
        }
        composeRule.onNodeWithText("Now playing").assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player").assertDoesNotExist()
    }

    private fun openVideo(id: String) {
        val video = allVideos().firstOrNull { it.id == id } ?: return
        state.value = state.value.copy(
            playback = PlaybackUiState(currentVideoId = id, queueVideoIds = listOf(id)),
            nowPlaying = NowPlayingUiState(video = video),
        )
    }

    private fun updateVideo(id: String, transform: (VideoUiModel) -> VideoUiModel) {
        state.value = state.value.copy(
            videos = state.value.videos.map { if (it.id == id) transform(it) else it },
            libraryVideos = state.value.libraryVideos.map { if (it.id == id) transform(it) else it },
            nowPlaying = state.value.nowPlaying.copy(
                video = state.value.nowPlaying.video?.let { if (it.id == id) transform(it) else it },
            ),
        )
    }

    private fun createPlaylist(title: String, ids: List<String>) {
        val playlist = PlaylistUiModel("playlist-1", title, "Local playlist", ids)
        state.value = state.value.copy(
            playlists = listOf(playlist),
            libraryVideos = state.value.libraryVideos.map { video ->
                if (video.id in ids) video.copy(playlistNames = listOf(title)) else video
            },
        )
    }

    private fun addToPlaylist(playlistId: String, ids: List<String>) {
        state.value = state.value.copy(
            playlists = state.value.playlists.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(videoIds = (playlist.videoIds + ids).distinct())
                } else {
                    playlist
                }
            },
        )
    }

    private fun allVideos() = state.value.videos + state.value.libraryVideos + state.value.search.videos

    private fun testState(): GrayjayUiState {
        val first = video("real-video-one", "A real plugin result", 1_000L)
        val second = video("real-video-two", "Another real result", 900L)
        return GrayjayUiState(
            videos = listOf(first),
            libraryVideos = listOf(first, second),
            sources = listOf(
                SourceUiModel(
                    id = "youtube",
                    engineId = "youtube-plugin-id",
                    name = "YouTube",
                    description = "Videos and creators",
                    accentColor = 0xFFE53935,
                    isEnabled = true,
                    pluginConfigUrl = "https://plugins.grayjay.app/Youtube/YoutubeConfig.json",
                    iconUrl = "https://plugins.grayjay.app/Youtube/youtube.png",
                ),
            ),
        )
    }

    private fun video(id: String, title: String, watchedAt: Long) = VideoUiModel(
        id = id,
        title = title,
        creator = "Creator",
        metadata = "10 views",
        duration = "1:00",
        contentUrl = "https://example.com/watch/$id",
        shareUrl = "https://example.com/watch/$id",
        thumbnailUrl = "https://example.com/$id.jpg",
        sourceName = "YouTube",
        sourceIconUrl = "https://plugins.grayjay.app/Youtube/youtube.png",
        lastWatchedAt = watchedAt,
        watchProgress = 0.25f,
    )
}
