package com.futo.platformplayer.backend

import kotlin.math.ceil

data class GrayjayStoryboard(
    val levels: List<GrayjayStoryboardLevel>,
)

data class GrayjayStoryboardLevel(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val columns: Int,
    val rows: Int,
    val intervalMs: Long,
    /** A signed YouTube sprite URL containing `$M` where the sheet index belongs. */
    val sheetUrlTemplate: String,
)

/** Parses YouTube's watch-page storyboard spec without coupling it to plugin models. */
internal object YouTubeStoryboardParser {
    fun parseWatchHtml(html: String, durationSeconds: Long): GrayjayStoryboard? {
        val spec = sequenceOf(
            "\"playerStoryboardSpecRenderer\"",
            "\"playerLiveStoryboardSpecRenderer\"",
        ).firstNotNullOfOrNull { marker ->
            extractJsonObjectString(
                source = html,
                objectMarker = marker,
                propertyName = "spec",
            )
        } ?: return null
        return parseSpec(spec, durationSeconds)
    }

    fun parseSpec(spec: String, durationSeconds: Long): GrayjayStoryboard? {
        val parts = spec.split('|')
        val baseUrl = parts.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
        val durationMs = durationSeconds.coerceAtLeast(0L) * 1_000L
        val levels = parts.drop(1).mapIndexedNotNull { levelIndex, encodedLevel ->
            val fields = encodedLevel.split('#')
            if (fields.size < 8) return@mapIndexedNotNull null

            val width = fields[0].toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapIndexedNotNull null
            val height = fields[1].toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapIndexedNotNull null
            val frameCount = fields[2].toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapIndexedNotNull null
            val columns = fields[3].toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapIndexedNotNull null
            val rows = fields[4].toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapIndexedNotNull null
            val declaredInterval = fields[5].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val intervalMs = declaredInterval.takeIf { it > 0L }
                ?: durationMs.takeIf { it > 0L }?.let {
                    ceil(it.toDouble() / frameCount.toDouble()).toLong().coerceAtLeast(1L)
                }
                ?: return@mapIndexedNotNull null
            val sheetName = fields[6]
            val signature = fields[7]
            val sheetCapacity = columns * rows
            val sheetCount = ceil(frameCount.toDouble() / sheetCapacity.toDouble()).toInt()

            val unsignedTemplate = baseUrl
                .replace("\$L", levelIndex.toString())
                .replace("\$N", sheetName)
            if (sheetCount > 1 && !unsignedTemplate.contains("\$M")) {
                return@mapIndexedNotNull null
            }
            val signedTemplate = if (
                signature.isBlank() || unsignedTemplate.contains("sigh=")
            ) {
                unsignedTemplate
            } else {
                unsignedTemplate + if (unsignedTemplate.contains('?')) "&sigh=$signature"
                else "?sigh=$signature"
            }

            GrayjayStoryboardLevel(
                width = width,
                height = height,
                frameCount = frameCount,
                columns = columns,
                rows = rows,
                intervalMs = intervalMs,
                sheetUrlTemplate = signedTemplate,
            )
        }
        return levels.takeIf(List<GrayjayStoryboardLevel>::isNotEmpty)?.let(::GrayjayStoryboard)
    }

    /**
     * Finds a string property inside an actual renderer object.
     *
     * Current watch pages list renderer names before the player response. Treating that list
     * entry as the object made the old parser consume an unrelated `spec` property and disabled
     * seek previews even though YouTube returned a valid storyboard.
     */
    private fun extractJsonObjectString(
        source: String,
        objectMarker: String,
        propertyName: String,
    ): String? {
        var markerStart = source.indexOf(objectMarker)
        while (markerStart >= 0) {
            var objectStart = markerStart + objectMarker.length
            while (objectStart < source.length && source[objectStart].isWhitespace()) objectStart += 1
            if (source.getOrNull(objectStart) != ':') {
                markerStart = source.indexOf(objectMarker, markerStart + objectMarker.length)
                continue
            }
            objectStart += 1
            while (objectStart < source.length && source[objectStart].isWhitespace()) objectStart += 1
            if (source.getOrNull(objectStart) != '{') {
                markerStart = source.indexOf(objectMarker, markerStart + objectMarker.length)
                continue
            }

            val objectEnd = findJsonObjectEnd(source, objectStart) ?: return null
            val propertyStart = source.indexOf("\"$propertyName\"", objectStart)
                .takeIf { it in (objectStart + 1) until objectEnd }
            if (propertyStart != null) {
                return extractJsonStringAtProperty(source, propertyStart, propertyName, objectEnd)
            }
            markerStart = source.indexOf(objectMarker, objectEnd + 1)
        }
        return null
    }

    private fun extractJsonStringAtProperty(
        source: String,
        propertyStart: Int,
        propertyName: String,
        objectEnd: Int,
    ): String? {
        val colon = source.indexOf(':', propertyStart + propertyName.length + 2)
        if (colon !in (propertyStart + 1) until objectEnd) return null
        val quoteStart = source.indexOf('"', colon + 1)
        if (quoteStart !in (colon + 1) until objectEnd) return null

        var escaped = false
        var index = quoteStart + 1
        while (index < objectEnd) {
            val character = source[index]
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                val literal = source.substring(quoteStart, index + 1)
                return decodeJsonStringLiteral(literal)
            }
            index += 1
        }
        return null
    }

    private fun findJsonObjectEnd(source: String, objectStart: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in objectStart until source.length) {
            val character = source[index]
            if (inString) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') inString = false
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun decodeJsonStringLiteral(literal: String): String? {
        if (literal.length < 2 || literal.first() != '"' || literal.last() != '"') return null
        val decoded = StringBuilder(literal.length - 2)
        var index = 1
        while (index < literal.lastIndex) {
            val character = literal[index]
            if (character != '\\') {
                decoded.append(character)
                index += 1
                continue
            }
            if (index + 1 >= literal.lastIndex) return null
            when (val escaped = literal[index + 1]) {
                '"', '\\', '/' -> decoded.append(escaped)
                'b' -> decoded.append('\b')
                'f' -> decoded.append('\u000C')
                'n' -> decoded.append('\n')
                'r' -> decoded.append('\r')
                't' -> decoded.append('\t')
                'u' -> {
                    if (index + 5 >= literal.length) return null
                    val codePoint = literal.substring(index + 2, index + 6)
                        .toIntOrNull(16) ?: return null
                    decoded.append(codePoint.toChar())
                    index += 4
                }
                else -> return null
            }
            index += 2
        }
        return decoded.toString()
    }
}
