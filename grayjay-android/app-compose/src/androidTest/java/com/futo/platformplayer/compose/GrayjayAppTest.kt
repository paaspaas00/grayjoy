package com.futo.platformplayer.compose
import androidx.compose.ui.test.swipeUp
import com.futo.platformplayer.compose.ui.HomeFeedType

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.futo.platformplayer.compose.ui.GrayjayApp
import com.futo.platformplayer.compose.ui.GrayjayUiState
import com.futo.platformplayer.compose.ui.HomeUiState
import com.futo.platformplayer.compose.ui.ExternalNavigationKind
import com.futo.platformplayer.compose.ui.ExternalNavigationUiModel
import com.futo.platformplayer.compose.ui.CastProtocolUi
import com.futo.platformplayer.compose.ui.ChromecastDeviceUiModel
import com.futo.platformplayer.compose.ui.ChromecastUiState
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
    private lateinit var pictureInPictureMode: MutableState<Boolean>
    private var installedSourceUrl: String? = null
    private var importPickerRequested = false
    private var newPipeImportPickerRequested = false
    private var confirmedImport: DatabaseImportSelection? = null
    private var trustedUnverifiedSource = false
    private var rejectedUnverifiedSource = false
    private var channelOpenRequests = 0

    @Before
    fun setUp() {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().containsKey("layoutAuditName")) {
            val configuration = android.content.res.Configuration(composeRule.activity.resources.configuration)
            configuration.setLocale(java.util.Locale.ITALIAN)
            @Suppress("DEPRECATION")
            composeRule.activity.resources.updateConfiguration(configuration, composeRule.activity.resources.displayMetrics)
            composeRule.runOnUiThread {
                androidx.core.view.WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView)
                    .isAppearanceLightStatusBars = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("layoutAuditDark") != "true"
            }
        }
        composeRule.runOnUiThread {
            player = ExoPlayer.Builder(composeRule.activity).build()
        }
        state = mutableStateOf(testState())
        pictureInPictureMode = mutableStateOf(false)
        composeRule.setContent {
            val audit = androidx.test.platform.app.InstrumentationRegistry.getArguments().containsKey("layoutAuditName")
            GrayjayTheme(dynamicColor = false, darkTheme = if (audit) androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("layoutAuditDark") == "true" else androidx.compose.foundation.isSystemInDarkTheme()) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDevicePerformanceProfile provides if (audit) DevicePerformanceProfile(false, false, true) else null,
            ) {
                GrayjayApp(
                    uiState = state.value,
                    player = player,
                    onDynamicColorsChange = {},
                    onPreferNewPipeForYoutubePlaybackChange = {},
                    onSubscriptionFetchModeChange = {},
                    onVideoTitleLanguageModeChange = {},
                    onPrivateSessionChange = { enabled ->
                        state.value = state.value.copy(privateSessionEnabled = enabled)
                    },
                    onOpenVideo = ::openVideo,
                    onLoadChannel = { channelOpenRequests++ },
                    onHomeFeedSelected = {},
                    onRefreshHome = {},
                    onPlayQueue = { ids -> ids.firstOrNull()?.let(::openVideo) },
                    onQueueVideos = {},
                    onPlayPlaylist = {},
                    onPlayPlaylistFrom = { playlistId, videoId ->
                        state.value = state.value.copy(playbackPlaylist = state.value.playlists.firstOrNull { it.id == playlistId })
                        openVideo(videoId)
                    },
                    onTogglePlayback = {},
                    onSkipToNext = {},
                    onSkipToPrevious = {},
                    onSeekPlaybackBy = {},
                    onPlaybackSpeedChange = {},
                    onUseChannelPlaybackSpeed = {},
                    onChannelPlaybackSpeedChange = { _, _ -> },
                    onVideoQualityChange = {},
                    onAudioLanguageChange = {},
                    onCaptionsEnabledChange = {},
                    onSubtitleLanguageChange = {},
                    onRetryPlayback = {},
                    onClosePlayback = {
                        state.value = state.value.copy(
                            playback = PlaybackUiState(),
                            nowPlaying = NowPlayingUiState(),
                            playbackPlaylist = null,
                        )
                    },
                    onToggleWatchLater = { id -> updateVideo(id) { it.copy(isWatchLater = !it.isWatchLater) } },
                    onToggleDownloaded = { id -> updateVideo(id) { it.copy(isDownloaded = !it.isDownloaded) } },
                    onToggleAudioDownloaded = {},
                    onDownloadVideo = { _, _ -> },
                    onDownloadAudio = { _, _ -> },
                    onDownloadVideos = { _, _ -> },
                    onDownloadPlaylist = { _, _ -> },
                    onCreatePlaylist = { title, ids -> createPlaylist(title, ids) },
                    onRenamePlaylist = { _, _ -> },
                    onAddVideosToPlaylist = { playlistId, ids -> addToPlaylist(playlistId, ids) },
                    onRemoveVideosFromPlaylist = { _, _ -> },
                    onReorderPlaylist = { _, _ -> },
                    onRemoveVideosFromHistory = {},
                    onRemovePlaylists = { ids ->
                        state.value = state.value.copy(
                            playlists = state.value.playlists.filterNot { it.id in ids },
                        )
                    },
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
                    onImportYoutube = { _, _ -> },
                    onDismissYoutubeImport = {},
                    onSearchQueryChange = { query ->
                        state.value = state.value.copy(search = state.value.search.copy(query = query))
                    },
                    onSearchSubmit = { query, type, _ ->
                        state.value = state.value.copy(
                            search = SearchUiState(
                                query = query,
                                hasSearched = true,
                                videos = state.value.videos.takeIf {
                                    type == com.futo.platformplayer.compose.ui.SearchContentType.Videos
                                }.orEmpty(),
                                channels = state.value.channels.takeIf {
                                    type == com.futo.platformplayer.compose.ui.SearchContentType.Creators
                                }.orEmpty(),
                                playlists = state.value.playlists.takeIf {
                                    type == com.futo.platformplayer.compose.ui.SearchContentType.Playlists
                                }.orEmpty(),
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
                    onPerChannelPlaybackSpeedChange = {},
                    onPreferredVideoQualityChange = {},
                    onPreferredAudioBitrateChange = {},
                    onPreferredAudioLanguageChange = {},
                    onPreferOriginalAudioChange = {},
                    onStickyCaptionsChange = {},
                    onShowRecommendationsChange = {},
                    onSearchHistoryChange = {},
                    onKeepScreenAwakeChange = {},
                    pictureInPictureMode = pictureInPictureMode.value,
                )
            }
            }
        }
    }

    @After
    fun tearDown() {
        if (::player.isInitialized) composeRule.runOnUiThread { player.release() }
    }

    @Test
    fun emptyHomeDoesNotRenderDemoOrContinueWatchingContent() {
        state.value = state.value.copy(videos = emptyList(), home = HomeUiState())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_empty_subscriptions)).assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE WATCHING").assertDoesNotExist()
        composeRule.onNodeWithText("Building a privacy-first media library").assertDoesNotExist()
    }

    @Test
    fun longPressVideoOffersShareAndPlaylistActions() {
        composeRule.onNodeWithTag("video-card-real-video-one").performScrollTo().performTouchInput {
            longClick()
        }

        composeRule.onNodeWithTag("video-action-share").assertIsDisplayed()
        composeRule.onNodeWithTag("video-action-playlist").assertIsDisplayed()
    }

    @Test
    fun historySupportsMultiSelectAndNewPlaylistCreation() {
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("library-filter-history").performClick()
        composeRule.onNodeWithTag("video-card-real-video-one").performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithTag("video-card-real-video-two").performScrollTo().performClick()
        composeRule.onNodeWithTag("history-add-to-playlist").performClick()
        composeRule.onNodeWithTag("new-playlist-name").performTextInput("Research")
        composeRule.onNodeWithTag("create-playlist").performClick()

        composeRule.runOnIdle {
            assertEquals("Research", state.value.playlists.single().title)
            assertEquals(2, state.value.playlists.single().videoIds.size)
        }
    }

    @Test
    fun channelFooterUsesCardSelectionGesturesAndRestoresNormalNavigation() {
        composeRule.onNodeWithTag("nav-library").performClick()
        val first = composeRule.onNodeWithTag("video-channel-footer-real-video-one", useUnmergedTree = true)
        val second = composeRule.onNodeWithTag("video-channel-footer-real-video-two", useUnmergedTree = true)
        first.performScrollTo().performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(1, channelOpenRequests) }
        composeRule.onNodeWithTag("nav-library").performClick()

        first.performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithTag("history-selection-bar").assertIsDisplayed()
        second.performScrollTo().performTouchInput { click(center) }
        assertSelectedVideoCount(2)
        first.performScrollTo().performTouchInput { click(center) }
        assertSelectedVideoCount(1)
        second.performScrollTo().performTouchInput { click(center) }
        composeRule.onNodeWithTag("history-selection-bar").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, channelOpenRequests) }

        first.performScrollTo().performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(2, channelOpenRequests) }
    }

    @Test
    fun playlistChannelFootersSelectUnselectedVideosWithoutOpeningChannels() {
        composeRule.runOnIdle {
            state.value = state.value.copy(playlists = listOf(
                PlaylistUiModel("footer-test", "Footer test", "", listOf("real-video-one", "real-video-two")),
            ))
        }
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("library-filter-playlists").performClick()
        composeRule.onNodeWithTag("playlist-footer-test").performScrollTo().performClick()
        composeRule.onNodeWithTag("video-channel-footer-real-video-one", useUnmergedTree = true)
            .performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithTag("playlist-selection-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("video-channel-footer-real-video-two", useUnmergedTree = true)
            .performScrollTo().performTouchInput { click(center) }
        assertSelectedVideoCount(2, "playlist-selection-bar")
        composeRule.runOnIdle { assertEquals(0, channelOpenRequests) }
    }

    private fun assertSelectedVideoCount(count: Int, barTag: String = "history-selection-bar") {
        composeRule.onNode(
            hasText(composeRule.activity.resources.getQuantityString(R.plurals.selected_count, count, count)) and
                hasAnyAncestor(hasTestTag(barTag)),
        ).assertIsDisplayed()
    }

    @Test
    fun playlistsSupportLongPressSelectionAndDeletion() {
        state.value = state.value.copy(
            playlists = listOf(
                PlaylistUiModel("playlist-one", "One", "Local playlist", listOf("real-video-one")),
                PlaylistUiModel("playlist-two", "Two", "Local playlist", listOf("real-video-two")),
            ),
        )
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("library-filter-playlists").performClick()
        composeRule.onNodeWithTag("playlist-playlist-one").performScrollTo().performTouchInput {
            longClick()
        }

        composeRule.onNodeWithTag("playlists-selection-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("playlists-remove").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.remove)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("playlist-two"), state.value.playlists.map(PlaylistUiModel::id))
        }
    }

    @Test
    fun sourceManagerShowsOptionsAndAcceptsConfigUrls() {
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("manage-sources"))
        composeRule.onNodeWithTag("manage-sources").performClick()

        composeRule.onNodeWithText(composeRule.activity.resources.getQuantityString(R.plurals.installed_sources, 1, 1)).assertIsDisplayed()
        composeRule.onNodeWithTag("source-youtube").assertIsOn().performClick().assertIsOff()
        composeRule.onNodeWithTag("add-source").performClick()
        composeRule.onNodeWithTag("source-url-field")
            .performTextInput("https://plugins.example/Config.json")
        composeRule.onNodeWithTag("install-source").performClick()

        composeRule.runOnIdle {
            assertEquals("https://plugins.example/Config.json", installedSourceUrl)
        }

        composeRule.onNodeWithText("YouTube").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.update_or_reinstall))
            .assertIsDisplayed()
    }

    @Test
    fun settingsLaunchesDatabasePickerAndPreviewConfirmsSelectedData() {
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("import-grayjay-database"))
        composeRule.onNodeWithTag("import-grayjay-database").performClick()
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
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("import-newpipe-database"))
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
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.trust_publisher_question)).assertIsDisplayed()
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
        composeRule.onNodeWithTag("video-card-real-video-one").assertDoesNotExist()

        composeRule.onNodeWithTag("search-field").performImeAction()

        composeRule.onNodeWithTag("video-card-real-video-one").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun duplicateSuggestionsFromSourcesDoNotCrashSearch() {
        composeRule.runOnIdle {
            state.value = state.value.copy(search = SearchUiState(
                query = "query", suggestions = listOf("Android", "android", " Android ", "Kotlin"),
            ))
        }
        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onAllNodesWithText("Android").assertCountEquals(1)
        composeRule.onNodeWithText("Kotlin").assertIsDisplayed()
    }

    @Test
    fun enteringSearchKeepsFocusAfterPreviousPageExitAnimation() {
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("search-field").assertIsFocused()
        composeRule.onNodeWithTag("search-field").performTextInput("query")
        composeRule.onNodeWithTag("search-field").assertIsFocused()
    }

    @Test
    fun playerCanCollapseAndExpandWithoutDuplicateMiniPlayer() {
        composeRule.onNodeWithTag("video-title-real-video-one", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.now_playing)).assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player").assertDoesNotExist()

        composeRule.onNodeWithTag("media-player").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + 1_300f), durationMillis = 700)
        }
        composeRule.onNodeWithTag("mini-player").assertIsDisplayed()

        composeRule.onNodeWithTag("mini-player").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 1_300f), durationMillis = 700)
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.now_playing)).assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player").assertDoesNotExist()
    }

    @Test
    fun pictureInPicturePreservesSearchTypeResultsAndScrollPosition() {
        val results = (1..40).map { index ->
            PlaylistUiModel("pip-result-$index", "Search playlist $index", "", emptyList())
        }
        composeRule.runOnIdle {
            state.value = state.value.copy(
                playlists = results,
                search = SearchUiState(
                    query = "playlist query",
                    hasSearched = true,
                    playlists = results,
                ),
            )
            openVideo("real-video-one")
        }
        composeRule.onNodeWithTag("nav-search").performClick()
        closeSoftKeyboard()
        composeRule.onNodeWithTag("search-filter-playlists").performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                search = SearchUiState(
                    query = "playlist query",
                    hasSearched = true,
                    playlists = results,
                ),
            )
        }
        composeRule.onNodeWithTag("search-results")
            .performScrollToNode(hasTestTag("playlist-pip-result-30"))
        composeRule.onNodeWithTag("playlist-pip-result-30").performScrollTo()
        val before = composeRule.onNodeWithTag("playlist-pip-result-30")
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.runOnIdle { pictureInPictureMode.value = true }
        composeRule.runOnIdle { pictureInPictureMode.value = false }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.back),
        ).performClick()

        composeRule.onNodeWithTag("playlist-pip-result-30").assertIsDisplayed()
        val after = composeRule.onNodeWithTag("playlist-pip-result-30")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 2f)
    }

    @Test
    fun shortsModeIsOptInAndSwipesChangeTheActualSelection() {
        val clips = listOf(video("short-one", "First short", 1L), video("short-two", "Second short", 2L))
            .map { it.copy(isShort = true, thumbnailUrl = "", sourceIconUrl = "") }
        composeRule.runOnIdle {
            state.value = state.value.copy(videos = clips, libraryVideos = clips,
                home = HomeUiState(selectedFeed = HomeFeedType.Shorts, videos = clips), brainrotShortsEnabled = false)
        }
        composeRule.onNodeWithTag("video-title-short-one", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("shorts-fullscreen-pager").assertDoesNotExist()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle { state.value = state.value.copy(brainrotShortsEnabled = true) }
        composeRule.onNodeWithTag("video-title-short-one", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("shorts-fullscreen-pager").assertIsDisplayed().performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertEquals("short-two", state.value.nowPlaying.video?.id) }
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("shorts-fullscreen-pager").assertDoesNotExist()
    }

    @Test
    fun collapsingPlaylistPlaybackFromHomeOpensItsPlaylist() {
        val playlist = PlaylistUiModel("active", "Active playlist", "", listOf("real-video-two", "real-video-one"))
        composeRule.runOnIdle { state.value = state.value.copy(playlists = listOf(playlist), playbackPlaylist = playlist) }
        composeRule.onNodeWithTag("video-title-real-video-one", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.back)).performClick()
        composeRule.onNodeWithTag("playlist-detail-active").assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player").assertIsDisplayed()
        composeRule.onNodeWithTag("current-playlist-video", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun playlistCollapseRestoresTheClickedScrollPositionNotTheCurrentTrack() {
        val videos = (1..45).map { video("scroll-$it", "Playlist video $it", 1_000L) }
        val playlist = PlaylistUiModel("scroll-origin", "Playlist", "", videos.map { it.id })
        composeRule.runOnIdle {
            state.value = state.value.copy(
                videos = videos, libraryVideos = videos, playlists = listOf(playlist),
                externalNavigation = ExternalNavigationUiModel(23L, ExternalNavigationKind.Playlist, playlist.id),
            )
        }
        composeRule.onNodeWithTag("playlist-detail-scroll-origin")
            .performScrollToNode(hasTestTag("video-card-scroll-30"))
        composeRule.onNodeWithTag("video-card-scroll-30").performScrollTo()
        val before = composeRule.onNodeWithTag("video-card-scroll-30").fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithTag("video-title-scroll-30", useUnmergedTree = true).performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                nowPlaying = state.value.nowPlaying.copy(video = videos[39]),
                playback = state.value.playback.copy(currentVideoId = "scroll-40"),
            )
        }
        composeRule.onNodeWithTag("media-player").performTouchInput {
            swipe(center, Offset(center.x, center.y + 1_300f), durationMillis = 700)
        }
        composeRule.onNodeWithTag("video-card-scroll-30").assertIsDisplayed()
        val after = composeRule.onNodeWithTag("video-card-scroll-30").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 2f)
        composeRule.onNodeWithTag("mini-player").assertIsDisplayed()
    }

    @Test
    fun closingPlaybackReturnsToItsPlaylistEvenAfterBrowsingSearch() {
        val playlist = PlaylistUiModel(
            "close-origin",
            "Close origin playlist",
            "",
            listOf("real-video-one", "real-video-two"),
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(
                playlists = listOf(playlist),
                externalNavigation = ExternalNavigationUiModel(
                    91L,
                    ExternalNavigationKind.Playlist,
                    playlist.id,
                ),
            )
        }
        composeRule.onNodeWithTag("video-title-real-video-one", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.back),
        ).performClick()
        composeRule.onNodeWithTag("nav-search").performClick()
        closeSoftKeyboard()
        composeRule.onNodeWithTag("mini-player").performClick()
        composeRule.onNodeWithTag("now-playing-close").performClick()

        composeRule.onNodeWithTag("playlist-detail-close-origin").assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player").assertDoesNotExist()
    }

    @Test
    fun channelOpenedFromPlaylistReturnsToThatPlaylist() {
        val playlist = PlaylistUiModel("origin", "Origin playlist", "", listOf("real-video-one"))
        val channel = com.futo.platformplayer.compose.ui.ChannelUiModel(
            "creator", "Creator", "youtube", "YouTube", 0, "", "",
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(
                playlists = listOf(playlist), channels = listOf(channel),
                videos = state.value.videos.map { it.copy(channelId = channel.id) },
                libraryVideos = state.value.libraryVideos.map { it.copy(channelId = channel.id) },
                externalNavigation = ExternalNavigationUiModel(22L, ExternalNavigationKind.Playlist, playlist.id),
            )
        }
        composeRule.onNodeWithTag("video-channel-footer-real-video-one").performScrollTo().performClick()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("playlist-detail-origin").assertIsDisplayed()
    }

    @Test
    fun searchMiniPlayerDoesNotInterceptNavigationBarTaps() {
        composeRule.runOnIdle { openVideo("real-video-one") }
        composeRule.onNodeWithTag("mini-player").assertIsDisplayed()
        for (retainNowPlaying in listOf(false, true)) {
            if (retainNowPlaying) {
                composeRule.onNodeWithTag("mini-player").performClick()
                composeRule.onNodeWithTag("now-playing-close").assertIsDisplayed()
                composeRule.onNodeWithContentDescription(
                    composeRule.activity.getString(R.string.back),
                ).performClick()
            }
            for (destination in listOf("home", "subscriptions", "library", "settings")) {
                composeRule.onNodeWithTag("nav-search").performClick()
                closeSoftKeyboard()
                composeRule.waitForIdle()

                // Semantics performClick bypasses hit testing and would miss a transparent
                // draggable layer covering the visible navigation buttons.
                composeRule.onNodeWithTag("nav-$destination").performTouchInput { click(center) }
                composeRule.onNodeWithTag("nav-$destination").assertIsSelected()
                composeRule.onNodeWithTag("mini-player").assertIsDisplayed()
            }
        }
    }

    @Test
    fun audioOnlyMiniPlayerShowsAudioArtwork() {
        val audio = video("audio-only", "Offline audio", 1_100L).copy(playbackAudioOnly = true)
        state.value = state.value.copy(
            // Feed/library copies represent the online video. Now Playing carries the resolved
            // audio-only download descriptor and must remain authoritative after collapsing.
            videos = listOf(audio.copy(playbackAudioOnly = false)),
            libraryVideos = listOf(audio.copy(playbackAudioOnly = false)),
            playback = PlaybackUiState(
                currentVideoId = audio.id,
                queueVideoIds = listOf(audio.id),
            ),
            nowPlaying = NowPlayingUiState(video = audio),
        )

        composeRule.onNodeWithTag("mini-player-audio-only", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.mini_player_audio)).assertIsDisplayed()
    }

    @Test
    fun castSheetSeparatesChromecastAndFCastDevicesIntoTabs() {
        openVideo("real-video-one")
        composeRule.onNodeWithTag("mini-player").performClick()
        state.value = state.value.copy(
            chromecast = ChromecastUiState(
                devices = listOf(
                    ChromecastDeviceUiModel("chromecast:living-room", "Living Room", CastProtocolUi.Chromecast),
                    ChromecastDeviceUiModel("fcast:desktop", "Desktop FCast", CastProtocolUi.FCast),
                ),
            ),
        )

        composeRule.onNodeWithTag("chromecast-button").performClick()
        composeRule.onNodeWithTag("cast-tab-chromecast").assertIsDisplayed()
        composeRule.onNodeWithText("Living Room").assertIsDisplayed()
        composeRule.onNodeWithText("Desktop FCast").assertDoesNotExist()

        composeRule.onNodeWithTag("cast-tab-fcast").performClick()
        composeRule.onNodeWithText("Desktop FCast").assertIsDisplayed()
        composeRule.onNodeWithText("Living Room").assertDoesNotExist()
    }

    @Test
    fun incomingPlaylistNavigationCancelsInFlightPlayerExpansion() {
        composeRule.onNodeWithTag("video-card-real-video-one").performScrollTo()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("video-title-real-video-one", useUnmergedTree = true).performClick()
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.runOnIdle {
                state.value = state.value.copy(
                    playlists = listOf(PlaylistUiModel("incoming", "Incoming", "", listOf("real-video-one"))),
                    externalNavigation = ExternalNavigationUiModel(1L, ExternalNavigationKind.Playlist, "incoming"),
                )
            }
            composeRule.mainClock.advanceTimeBy(600)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithTag("now-playing-close").assertDoesNotExist()
        composeRule.onNodeWithTag("playlist-detail-incoming").assertIsDisplayed()
    }

    @Test
    fun captureResponsiveScreens() {
        val name = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("layoutAuditName")
        org.junit.Assume.assumeTrue(name != null)
        require(name!!.matches(Regex("[a-zA-Z0-9_-]+")))
        val directory = java.io.File(composeRule.activity.getExternalFilesDir(null), "layout-audit/$name").apply { mkdirs() }
        val configuration = composeRule.activity.resources.configuration
        val compactAudit = com.futo.platformplayer.compose.ui.screens.useCompactUi(configuration.screenWidthDp, configuration.screenHeightDp, configuration.fontScale)
        fun capture(screen: String) {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(350)
            // System window/IME animations use real time, not the Compose test clock.
            android.os.SystemClock.sleep(250L)
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            requireNotNull(bitmap)
            java.io.File(directory, "$screen.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            java.io.File(directory, "$screen.txt").writeText(composeRule.onAllNodes(isRoot(), useUnmergedTree = true).printToString())
        }
        val titles = listOf("Una prospettiva diversa", "Il primo passo", "Camminare nello spazio: una storia di coraggio, ingegneria e nuove possibilità", "La Terra vista da lassù", "Le tracce restano", "Fuori dalla navicella")
        val images = listOf("audit_earth", "audit_moon", "audit_spacewalk")
        val testPackage = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.packageName
        val videos = (1..35).map { index -> video("audit-$index", titles[(index - 1) % titles.size], 1_000L).copy(
            creator = "Laboratorio delle idee", metadata = "123.456 visualizzazioni · 2 giorni fa",
            thumbnailUrl = "android.resource://$testPackage/drawable/${images[(index - 1) % images.size]}", sourceIconUrl = "", channelId = "audit-channel",
            description = "Una descrizione piuttosto lunga per verificare la leggibilità del testo e lo scorrimento della pagina. ".repeat(5),
        ) }
        val playlist = PlaylistUiModel("audit-playlist", "Playlist con un nome lungo per verificare il layout", "", videos.map { it.id })
        val remote = playlist.copy(id = "audit-remote", sourceId = "youtube")
        val channel = com.futo.platformplayer.compose.ui.ChannelUiModel("audit-channel", "Laboratorio delle idee", "youtube", "YouTube", 0, "12.345 iscritti", "Esperimenti, approfondimenti e curiosità dal mondo della tecnologia. ".repeat(4), thumbnailUrl = "android.resource://$testPackage/drawable/audit_moon")
        composeRule.runOnIdle {
            state.value = state.value.copy(videos = videos, libraryVideos = videos, playlists = listOf(playlist), channels = listOf(channel),
                home = HomeUiState(videos = videos), subscriptionVideos = videos, followingVideos = videos,
                followedCreatorIds = setOf(channel.id), followingFeedLoaded = true,
                search = SearchUiState(query = "Tecnologia e curiosità", hasSearched = true, videos = videos),
                channelDetail = com.futo.platformplayer.compose.ui.ChannelDetailUiState(channelId = channel.id, channel = channel, videos = videos, isLoaded = true, supportsShorts = true, supportsPlaylists = true, playlists = listOf(remote)),
                remotePlaylistDetail = com.futo.platformplayer.compose.ui.RemotePlaylistDetailUiState(playlist = remote, videos = videos),
            )
        }
        capture("01-home")
        if (compactAudit) {
            val first = composeRule.onNodeWithTag("video-card-audit-1").fetchSemanticsNode().boundsInRoot
            assertTrue("Compact Home should give its first screen to videos, not headings", first.top < composeRule.activity.resources.displayMetrics.heightPixels * 0.3f)
        }
        composeRule.onNodeWithTag("nav-subscriptions").performClick(); capture("02-subscriptions")
        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-field").performClick(); capture("03-search-keyboard")
        closeSoftKeyboard(); capture("04-search")
        composeRule.onNodeWithTag("nav-library").performClick(); capture("05-library")
        composeRule.onNodeWithTag("nav-settings").performClick(); capture("06-settings")
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("advanced-settings"))
        composeRule.onNodeWithTag("advanced-settings").performClick(); capture("06b-advanced")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.close)).performClick()
        if (compactAudit) {
            composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("default-playback-speed"))
            composeRule.onNodeWithTag("default-playback-speed").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("version-information")); capture("07-settings-bottom")
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("manage-sources"))
        composeRule.onNodeWithTag("manage-sources").performScrollTo().performClick(); capture("08-sources")
        composeRule.runOnIdle { state.value = state.value.copy(externalNavigation = ExternalNavigationUiModel(80L, ExternalNavigationKind.Playlist, playlist.id)) }
        capture("09-playlist")
        if (compactAudit) {
            composeRule.onNodeWithTag("video-card-audit-1").assertIsDisplayed()
            composeRule.onNodeWithTag("playlist-more-actions").performClick()
            composeRule.onNodeWithTag("playlist-download-audio").assertIsDisplayed()
            composeRule.onNodeWithTag("playlist-download-video").assertIsDisplayed()
            capture("09b-playlist-actions")
            composeRule.onNodeWithTag("rename-current-playlist").performTouchInput { click(center) }
            composeRule.onNodeWithTag("rename-playlist-name").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
        }
        composeRule.onNodeWithTag("playlist-detail-${playlist.id}").performScrollToNode(hasTestTag("playlist-open-reorder"))
        composeRule.onNodeWithTag("playlist-open-reorder").performClick(); capture("10-reorder")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.cancel)).performClick()
        composeRule.onNodeWithTag("video-card-audit-1").performScrollTo().performTouchInput { longClick() }; capture("11-selection")
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("playlist-detail-${playlist.id}").performScrollToNode(hasTestTag("video-card-audit-1"))
        composeRule.onNodeWithTag("video-title-audit-1", useUnmergedTree = true).performScrollTo().performClick(); capture("12-nowplaying")
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.back)).performClick(); capture("13-miniplayer")
        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-field").performClick(); capture("13b-miniplayer-keyboard")
        closeSoftKeyboard()
        composeRule.runOnIdle { state.value = state.value.copy(externalNavigation = ExternalNavigationUiModel(81L, ExternalNavigationKind.Channel, channel.id)) }
        capture("14-channel")
        composeRule.onNodeWithTag("channel-detail-${channel.id}").performScrollToNode(hasTestTag("channel-video-search"))
        composeRule.onNodeWithTag("channel-video-search").performClick()
        capture("15-channel-search")
        closeSoftKeyboard()
        composeRule.runOnIdle { state.value = state.value.copy(externalNavigation = ExternalNavigationUiModel(82L, ExternalNavigationKind.Playlist, remote.id)) }
        capture("16-remote-playlist")
        if (com.futo.platformplayer.compose.ui.screens.supportsShortsFeedPlayer(configuration.screenWidthDp, configuration.screenHeightDp, configuration.smallestScreenWidthDp)) {
            composeRule.onNodeWithTag("nav-home").performClick()
            val clips = videos.take(3).map { it.copy(isShort = true) }
            composeRule.runOnIdle { state.value = state.value.copy(videos = clips, home = HomeUiState(selectedFeed = HomeFeedType.Shorts, videos = clips), brainrotShortsEnabled = true) }
            composeRule.onNodeWithTag("video-title-audit-1", useUnmergedTree = true).performScrollTo().performClick()
            capture("17-shorts")
            composeRule.onNodeWithTag("shorts-fullscreen-pager").performTouchInput { swipeUp() }
            capture("18-shorts-next")
        }
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
            home = HomeUiState(videos = listOf(first)),
            libraryVideos = listOf(first, second),
            sources = listOf(
                SourceUiModel(
                    id = "youtube",
                    engineId = "youtube-plugin-id",
                    pluginConfigPath = "sources/youtube/YoutubeConfig.json",
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
