package com.futo.platformplayer.compose.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.futo.platformplayer.compose.LocalDevicePerformanceProfile
import com.futo.platformplayer.compose.devicePerformanceProfile
import com.futo.platformplayer.compose.data.YOUTUBE_SOURCE_ACCENT

private val LightColors = lightColorScheme(
    primary = Color(0xFF405DB1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF4F91DC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4FF),
    onSecondaryContainer = Color(0xFF001C3A),
    tertiary = Color(0xFF006875),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9EEFFD),
    onTertiaryContainer = Color(0xFF001F24),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF082B80),
    primaryContainer = Color(0xFF274496),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFA5CAFF),
    onSecondary = Color(0xFF00315E),
    secondaryContainer = Color(0xFF174975),
    onSecondaryContainer = Color(0xFFD4E4FF),
    tertiary = Color(0xFF82D3E0),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF004F59),
    onTertiaryContainer = Color(0xFF9EEFFD),
)

@Composable
fun GrayjayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    musicProfile: Boolean = false,
    sourceAccentColor: Long? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = remember(context, configuration, dynamicColor, darkTheme, musicProfile, sourceAccentColor) {
        val base = when {
            musicProfile && darkTheme -> DarkMusicColors
            musicProfile -> LightMusicColors
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
                dynamicDarkColorScheme(context)
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }
        if (sourceAccentColor == null || musicProfile) base
        else sourceProfileColorScheme(base, sourceAccentColor, darkTheme)
    }
    val performance = remember(context) { devicePerformanceProfile(context) }

    CompositionLocalProvider(LocalDevicePerformanceProfile provides performance) {
        MaterialTheme(
            colorScheme = colors,
            typography = GrayjayTypography,
            shapes = GrayjayShapes,
            content = content,
        )
    }
}

internal fun sourceProfileColorScheme(base: ColorScheme, sourceAccentColor: Long, darkTheme: Boolean): ColorScheme {
    if (darkTheme && sourceAccentColor == YOUTUBE_SOURCE_ACCENT) {
        // Dark surfaces need their own red tones: a white blend of the deep light-theme
        // accent looked dusty pink, while a black blend made selected chips disappear.
        return base.copy(
            primary = Color(0xFFD9625C), onPrimary = Color(0xFF320705),
            primaryContainer = Color(0xFF602024), onPrimaryContainer = Color(0xFFFFE5E2),
            secondary = Color(0xFFD9625C), onSecondary = Color(0xFF320705),
            secondaryContainer = Color(0xFF602024), onSecondaryContainer = Color(0xFFFFE5E2),
            background = Color(0xFF141112), surface = Color(0xFF141112),
            surfaceContainerLow = Color(0xFF1D1819), surfaceContainer = Color(0xFF241E1F),
            surfaceContainerHigh = Color(0xFF302729), surfaceContainerHighest = Color(0xFF3A3032),
        )
    }
    val brand = Color(sourceAccentColor)
    var primary = if (darkTheme) lerp(brand, Color.White, .52f) else brand
    if (!darkTheme) repeat(12) {
        if (1.05f / (primary.luminance() + .05f) < 4.5f) primary = lerp(primary, Color.Black, .08f)
    }
    val container = lerp(brand, if (darkTheme) Color.Black else Color.White, if (darkTheme) .6f else .85f)
    return base.copy(
        primary = primary, onPrimary = if (darkTheme) Color.Black else Color.White,
        primaryContainer = container, onPrimaryContainer = if (darkTheme) Color.White else Color.Black,
        secondary = primary, onSecondary = if (darkTheme) Color.Black else Color.White,
        secondaryContainer = container, onSecondaryContainer = if (darkTheme) Color.White else Color.Black,
        background = lerp(base.background, brand, .025f),
        surface = lerp(base.surface, brand, .025f),
        surfaceContainer = lerp(base.surfaceContainer, brand, .055f),
        surfaceContainerLow = lerp(base.surfaceContainerLow, brand, .025f),
    )
}

private val LightMusicColors = lightColorScheme(
    primary = Color(0xFF146C36), onPrimary = Color.White,
    primaryContainer = Color(0xFFA2F5B4), onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF506352), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8D2), onSecondaryContainer = Color(0xFF0E1F12),
    tertiary = Color(0xFF3A656C), tertiaryContainer = Color(0xFFBEEAF1),
    background = Color(0xFFF5FBF2), surface = Color(0xFFF5FBF2),
    surfaceContainerLow = Color(0xFFEFF5EC), surfaceContainer = Color(0xFFE9EFE6),
    surfaceContainerHigh = Color(0xFFE3E9E0), surfaceContainerHighest = Color(0xFFDDE4DA),
)

private val DarkMusicColors = darkColorScheme(
    primary = Color(0xFF86D99A), onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005323), onPrimaryContainer = Color(0xFFA2F5B4),
    secondary = Color(0xFFB7CCB7), onSecondary = Color(0xFF233426),
    secondaryContainer = Color(0xFF394B3B), onSecondaryContainer = Color(0xFFD3E8D2),
    tertiary = Color(0xFFA2CED5), tertiaryContainer = Color(0xFF204D54),
    background = Color(0xFF0E1510), surface = Color(0xFF0E1510),
    surfaceContainerLow = Color(0xFF171D18), surfaceContainer = Color(0xFF1B211C),
    surfaceContainerHigh = Color(0xFF252B26), surfaceContainerHighest = Color(0xFF303631),
)
