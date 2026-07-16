package com.futo.platformplayer.compose.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = GrayjayTypography,
        shapes = GrayjayShapes,
        content = content,
    )
}
