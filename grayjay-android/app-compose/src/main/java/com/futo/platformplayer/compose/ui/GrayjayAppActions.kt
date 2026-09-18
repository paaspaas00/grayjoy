package com.futo.platformplayer.compose.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockCategory
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockRule

/**
 * Stable event surface for the root UI.
 *
 * Keeping these callbacks off the composable method signature avoids oversized DEX methods
 * that some ART verifiers reject before the first frame is composed.
 */
@Stable
class GrayjayAppActions {
    var onUiLanguageChange: (String) -> Unit = {}
    var onDarkThemeChange: (Boolean) -> Unit = {}
    var onThemeModeChange: (ThemeMode) -> Unit = {}
    var onDynamicColorsChange: (Boolean) -> Unit = { _ -> }
    var onPrivateSessionChange: (Boolean) -> Unit = { _ -> }
    var onOpenVideo: (String) -> Unit = { _ -> }
    var onDismissVideoOpenDialog: () -> Unit = {}
    var onLoadChannel: (ChannelUiModel) -> Unit = { _ -> }
    var onChannelTabSelected: (ChannelContentTab) -> Unit = {}
    var onHomeFeedSelected: (HomeFeedType) -> Unit = { _ -> }
    var onHomeBrowseTabSelected: (String, String) -> Unit = { _, _ -> }
    var onHomeBrowseOptionSelected: (String, String, String) -> Unit = { _, _, _ -> }
    var onRefreshHome: () -> Unit = {}
    var onLoadMoreHome: () -> Unit = {}
    var onPlayQueue: (List<String>) -> Unit = { _ -> }
    var onQueueVideos: (List<String>) -> Unit = { _ -> }
    var onPlayNext: (String) -> Unit = {}
    var onPlayPlaylist: (String) -> Unit = { _ -> }
    var onPlayPlaylistFrom: (String, String) -> Unit = { _, _ -> }
    var onTogglePlayback: () -> Unit = {}
    var onSkipToNext: () -> Unit = {}
    var onSkipToPrevious: () -> Unit = {}
    var onSeekPlaybackBy: (Long) -> Unit = { _ -> }
    var onPlaybackSpeedChange: (Float) -> Unit = { _ -> }
    var onSpeedHoldStart: () -> Unit = {}
    var onSpeedHoldEnd: () -> Unit = {}
    var onUseChannelPlaybackSpeed: () -> Unit = {}
    var onChannelPlaybackSpeedChange: (String, Float?) -> Unit = { _, _ -> }
    var onVideoQualityChange: (Int?) -> Unit = { _ -> }
    var onAudioLanguageChange: (String?) -> Unit = { _ -> }
    var onCaptionsEnabledChange: (Boolean) -> Unit = { _ -> }
    var onSubtitleLanguageChange: (String?) -> Unit = { _ -> }
    var onRetryPlayback: () -> Unit = {}
    var onStoryboardLoadFailure: (String) -> Unit = {}
    var onClosePlayback: () -> Unit = {}
    var onToggleWatchLater: (String) -> Unit = { _ -> }
    var onToggleDownloaded: (String) -> Unit = { _ -> }
    var onToggleAudioDownloaded: (String) -> Unit = { _ -> }
    var onDownloadVideo: (String, Int?) -> Unit = { _, _ -> }
    var onDownloadAudio: (String, Int?) -> Unit = { _, _ -> }
    var onDownloadVideos: (List<String>, DownloadMediaType) -> Unit = { _, _ -> }
    var onDownloadPlaylist: (String, DownloadMediaType) -> Unit = { _, _ -> }
    var onCancelDownloadPlaylist: (String, DownloadMediaType) -> Unit = { _, _ -> }
    var onCreatePlaylist: (String, List<String>) -> Unit = { _, _ -> }
    var onRenamePlaylist: (String, String) -> Unit = { _, _ -> }
    var onAddVideosToPlaylist: (String, List<String>) -> Unit = { _, _ -> }
    var onRemoveVideosFromPlaylist: (String, List<String>) -> Unit = { _, _ -> }
    var onReorderPlaylist: (String, List<String>) -> Unit = { _, _ -> }
    var onRemoveVideosFromHistory: (List<String>) -> Unit = { _ -> }
    var onRemoveDownloads: (List<String>) -> Unit = {}
    var onRemovePlaylists: (List<String>) -> Unit = {}
    var onExportDownloads: (List<String>, DownloadMediaType, Uri) -> Unit = { _, _, _ -> }
    var onSeekPlayback: (Float) -> Unit = { _ -> }
    var onSourceEnabledChange: (String, Boolean) -> Unit = { _, _ -> }
    var onInstallSource: (String) -> Unit = { _ -> }
    var onScanSourceQr: () -> Unit = {}
    var onRefreshSource: (String) -> Unit = { _ -> }
    var onClearSourceCache: (String) -> Unit = { _ -> }
    var onRemoveSource: (String) -> Unit = { _ -> }
    var onLoginSource: (SourceUiModel) -> Unit = { _ -> }
    var onLogoutSource: (String) -> Unit = { _ -> }
    var onImportYoutube: (String, YoutubeImportSelection) -> Unit = { _, _ -> }
    var onDismissYoutubeImport: () -> Unit = {}
    var onSearchQueryChange: (String) -> Unit = { _ -> }
    var onSearchSubmit: (String, SearchContentType, Set<String>) -> Unit = { _, _, _ -> }
    var onSourceFilterSelectionChange: (String, String, String) -> Unit = { _, _, _ -> }
    var onLoadMoreSearch: () -> Unit = {}
    var onLoadMoreChannel: () -> Unit = {}
    var onChannelSearchQueryChange: (String) -> Unit = {}
    var onLoadMoreChannelSearch: () -> Unit = {}
    var onLoadFollowingComplete: () -> Unit = {}
    var onLoadRemotePlaylist: (PlaylistUiModel) -> Unit = {}
    var onLoadMoreRemotePlaylist: () -> Unit = {}
    var onPlayRemotePlaylist: () -> Unit = {}
    var onPlayRemotePlaylistFrom: (String) -> Unit = {}
    var onDownloadRemotePlaylist: (DownloadMediaType) -> Unit = {}
    var onCancelDownloadRemotePlaylist: (DownloadMediaType) -> Unit = {}
    var onCreateLocalPlaylistFromRemote: (String) -> Unit = {}
    var onLoadMoreRecommendations: () -> Unit = {}
    var onLoadMoreComments: () -> Unit = {}
    var onOpenCommentReplies: (String) -> Unit = {}
    var onDismissCommentReplies: () -> Unit = {}
    var onLoadMoreCommentReplies: () -> Unit = {}
    var onToggleFollowing: () -> Unit = {}
    var onResumeFromHistory: () -> Unit = {}
    var onCreatorFollowedChange: (String, Boolean) -> Unit = { _, _ -> }
    var onChooseDatabaseImport: () -> Unit = {}
    var onChooseNewPipeImport: () -> Unit = {}
    var onRetryDatabaseImport: (String) -> Unit = { _ -> }
    var onConfirmDatabaseImport: (DatabaseImportSelection) -> Unit = { _ -> }
    var onDismissDatabaseImport: () -> Unit = {}
    var onTrustUnverifiedSource: () -> Unit = {}
    var onRejectUnverifiedSource: () -> Unit = {}
    var onSwitchProfile: (String) -> Unit = { _ -> }
    var onCreateProfile: (String, String) -> Unit = { _, _ -> }
    var onVerifyProfilePin: (String, String) -> Boolean = { _, _ -> false }
    var onRenameProfile: (String, String) -> Unit = { _, _ -> }
    var onSetProfileDeviceCredentialProtection: (String, Boolean) -> Unit = { _, _ -> }
    var onDeleteProfile: (String) -> Unit = {}
    var onDefaultPlaybackSpeedChange: (Float) -> Unit = { _ -> }
    var onPerChannelPlaybackSpeedChange: (Boolean) -> Unit = { _ -> }
    var onHoldToSpeedChange: (Boolean) -> Unit = {}
    var onSponsorBlockEnabledChange: (Boolean) -> Unit = {}
    var onSponsorBlockSkipNoticesEnabledChange: (Boolean) -> Unit = {}
    var onSponsorBlockCategoriesChange: (Set<SponsorBlockCategory>) -> Unit = {}
    var onChannelSponsorBlockOverrideChange: (String, SponsorBlockRule?) -> Unit = { _, _ -> }
    var onVideoSponsorBlockOverrideChange: (String, SponsorBlockRule?) -> Unit = { _, _ -> }
    var onPreferredVideoQualityChange: (Int) -> Unit = { _ -> }
    var onPreferredAudioBitrateChange: (Int) -> Unit = { _ -> }
    var onPreferredAudioLanguageChange: (String) -> Unit = { _ -> }
    var onPreferOriginalAudioChange: (Boolean) -> Unit = { _ -> }
    var onPreferNewPipeForYoutubePlaybackChange: (Boolean) -> Unit = { _ -> }
    var onSubscriptionFetchModeChange: (SubscriptionFetchMode) -> Unit = { _ -> }
    var onVideoTitleLanguageModeChange: (VideoTitleLanguageMode) -> Unit = { _ -> }
    var onStickyCaptionsChange: (Boolean) -> Unit = { _ -> }
    var onShowRecommendationsChange: (Boolean) -> Unit = { _ -> }
    var onSearchHistoryChange: (Boolean) -> Unit = { _ -> }
    var onCrashLoggingChange: (Boolean) -> Unit = {}
    var onKeepScreenAwakeChange: (Boolean) -> Unit = { _ -> }
    var onPictureInPictureChange: (Boolean) -> Unit = {}
    var onStartChromecastDiscovery: () -> Unit = {}
    var onConnectChromecast: (String) -> Unit = {}
    var onDisconnectChromecast: () -> Unit = {}
    var onOtherAudioDuckingChange: (Boolean) -> Unit = {}
    var onOtherAudioDuckVolumeChange: (Int) -> Unit = {}
    var onScanPcPairingQr: () -> Unit = {}
    var onRemovePairedComputer: (String) -> Unit = {}
    var onPlayFromComputer: (String) -> Unit = {}
    var onToggleComputerPlayback: (String) -> Unit = {}
    var onPreviousComputerPlayback: (String) -> Unit = {}
    var onNextComputerPlayback: (String) -> Unit = {}
    var onSeekComputerPlayback: (String, Long) -> Unit = { _, _ -> }
    var onExternalNavigationHandled: (Long) -> Unit = {}
    var onCheckForUpdates: () -> Unit = {}
    var onInstallUpdate: (ReleaseUpdateUiModel) -> Unit = {}
    var onCancelUpdateDownload: () -> Unit = {}
    var onHydrateVideoMetadata: (String) -> Unit = {}
    var onHydrateChannelArtwork: (String) -> Unit = {}
    var onBrainrotShortsChange: (Boolean) -> Unit = {}
    var onRebuildContentCaches: () -> Unit = {}
    var onFullscreenPresentationChanged: (Boolean, Boolean) -> Unit = { _, _ -> }
}
