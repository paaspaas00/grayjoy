package com.futo.platformplayer.compose.ui
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.futo.platformplayer.compose.R

internal val LocalYoutubeOnly = staticCompositionLocalOf { false }
internal fun List<SourceUiModel>.isYoutubeOnly(): Boolean =
    filter(SourceUiModel::isEnabled).map { it.id.lowercase(java.util.Locale.ROOT) }.toSet() == setOf("youtube")
internal fun sourceTerminologyResource(id: Int, youtubeOnly: Boolean): Int = if (!youtubeOnly) id else when (id) {
    R.string.creator -> R.string.channel_label
    R.string.creators -> R.string.channels_label
    R.string.your_creators -> R.string.your_channels
    R.string.home_empty_subscriptions -> R.string.home_empty_channels
    R.string.no_followed_creators -> R.string.no_followed_channels
    R.string.creator_load_failed -> R.string.channel_load_failed
    R.string.imported_creator -> R.string.imported_channel
    R.string.unknown_creator -> R.string.unknown_channel
    R.string.search_hint -> R.string.search_channels_hint
    R.plurals.followed_creators -> R.plurals.followed_channels
    else -> id
}
@Composable internal fun sourceStringResource(id: Int): String =
    stringResource(com.futo.platformplayer.compose.ui.screens.musicTerminologyResource(
        sourceTerminologyResource(id, LocalYoutubeOnly.current),
        com.futo.platformplayer.compose.ui.screens.LocalMusicPresentation.current))
@Composable internal fun sourcePluralStringResource(id: Int, quantity: Int, vararg args: Any): String =
    pluralStringResource(sourceTerminologyResource(id, LocalYoutubeOnly.current), quantity, *args)

@Composable internal fun sourceCreatorFallback(value: String): String =
    if (LocalYoutubeOnly.current && (value == stringResource(R.string.creator) || value.equals("Creator", true)))
        stringResource(R.string.channel_label) else value
