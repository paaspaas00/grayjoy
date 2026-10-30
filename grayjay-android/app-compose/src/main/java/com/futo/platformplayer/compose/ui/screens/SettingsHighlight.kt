package com.futo.platformplayer.compose.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight

internal val LocalSettingsSearchQuery = compositionLocalOf { "" }

internal fun highlightSettingsMatches(text: String, query: String, background: Color, foreground: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val positions = mutableListOf<Int>()
    val normalized = buildString {
        text.forEachIndexed { index, char ->
            val part = normalizeSettingsQuery(char.toString())
            append(part); repeat(part.length) { positions.add(index) }
        }
    }
    return buildAnnotatedString {
        append(text)
        normalizeSettingsQuery(query).split(Regex("\\s+")).filter(String::isNotBlank).distinct().forEach { word ->
            var start = normalized.indexOf(word)
            while (start >= 0) {
                addStyle(SpanStyle(background = background, color = foreground, fontWeight = FontWeight.Bold),
                    positions[start], positions[start + word.length - 1] + 1)
                start = normalized.indexOf(word, start + word.length)
            }
        }
    }
}
@Composable
internal fun settingsHighlightedText(text: String): AnnotatedString {
    val query = LocalSettingsSearchQuery.current
    val colors = MaterialTheme.colorScheme
    return remember(text, query, colors.primaryContainer, colors.onPrimaryContainer) {
        highlightSettingsMatches(text, query, colors.primaryContainer, colors.onPrimaryContainer)
    }
}
