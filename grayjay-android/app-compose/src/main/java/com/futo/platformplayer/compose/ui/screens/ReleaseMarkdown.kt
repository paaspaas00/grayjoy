package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal sealed interface ReleaseMarkdownBlock {
    data class Heading(val level: Int, val text: String) : ReleaseMarkdownBlock
    data class Bullet(val marker: String, val text: String) : ReleaseMarkdownBlock
    data class Paragraph(val text: String) : ReleaseMarkdownBlock
}

internal fun parseReleaseMarkdown(markdown: String): List<ReleaseMarkdownBlock> = buildList {
    val paragraph = mutableListOf<String>()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            add(ReleaseMarkdownBlock.Paragraph(paragraph.joinToString(" ")))
            paragraph.clear()
        }
    }
    markdown.lineSequence().forEach { sourceLine ->
        val line = sourceLine.trim()
        when {
            line.isBlank() -> flushParagraph()
            HEADING.matchEntire(line) != null -> {
                flushParagraph()
                val match = HEADING.matchEntire(line)!!
                add(
                    ReleaseMarkdownBlock.Heading(
                        level = match.groupValues[1].length,
                        text = match.groupValues[2],
                    ),
                )
            }
            UNORDERED.matchEntire(line) != null -> {
                flushParagraph()
                add(
                    ReleaseMarkdownBlock.Bullet(
                        marker = "•",
                        text = UNORDERED.matchEntire(line)!!.groupValues[1],
                    ),
                )
            }
            ORDERED.matchEntire(line) != null -> {
                flushParagraph()
                val match = ORDERED.matchEntire(line)!!
                add(
                    ReleaseMarkdownBlock.Bullet(
                        marker = "${match.groupValues[1]}.",
                        text = match.groupValues[2],
                    ),
                )
            }
            else -> paragraph += line
        }
    }
    flushParagraph()
}

@Composable
internal fun ReleaseMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseReleaseMarkdown(markdown).forEach { block ->
            when (block) {
                is ReleaseMarkdownBlock.Heading -> Text(
                    text = markdownInline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                )
                is ReleaseMarkdownBlock.Bullet -> Row {
                    Text(
                        text = block.marker,
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = markdownInline(block.text),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is ReleaseMarkdownBlock.Paragraph -> Text(
                    text = markdownInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun markdownInline(source: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        val boldStart = source.indexOf("**", index)
        val codeStart = source.indexOf('`', index)
        val markerStart = listOf(boldStart, codeStart).filter { it >= 0 }.minOrNull()
        if (markerStart == null) {
            append(source.substring(index))
            break
        }
        append(source.substring(index, markerStart))
        if (markerStart == boldStart) {
            val end = source.indexOf("**", markerStart + 2)
            if (end < 0) {
                append(source.substring(markerStart))
                break
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(source.substring(markerStart + 2, end))
            pop()
            index = end + 2
        } else {
            val end = source.indexOf('`', markerStart + 1)
            if (end < 0) {
                append(source.substring(markerStart))
                break
            }
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
            append(source.substring(markerStart + 1, end))
            pop()
            index = end + 1
        }
    }
}

private val HEADING = Regex("^(#{1,3})\\s+(.+)$")
private val UNORDERED = Regex("^[-*+]\\s+(.+)$")
private val ORDERED = Regex("^(\\d+)[.)]\\s+(.+)$")
