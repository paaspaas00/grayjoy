package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginFilterConfigTest {
    @Test
    fun parsesSupportedGroupsAndOptions() {
        val groups = parseFilterGroups(
            """
            {
              "version": 1,
              "filters": [{
                "id": "section",
                "label": "Section",
                "scopes": ["home", "search"],
                "default": "all",
                "options": [
                  {"id": "all", "label": "All", "value": "all"},
                  {"id": "recent", "label": "Recent", "value": "recent"}
                ]
              }]
            }
            """.trimIndent(),
        )

        assertEquals(1, groups.size)
        assertEquals(setOf("home", "search"), groups.single().scopes)
        assertEquals("all", groups.single().defaultValue)
        assertEquals(listOf("all", "recent"), groups.single().options.map { it.value })
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertTrue(parseFilterGroups("""{"version":2,"filters":[]}""").isEmpty())
    }

    @Test
    fun parsesGenericImageHeaders() {
        assertEquals(
            mapOf("Referer" to "https://example.com/"),
            parseImageRequestHeaders(
                """{"imageRequestHeaders":{"Referer":"https://example.com/"}}""",
            ),
        )
    }

    @Test
    fun richDescriptionBecomesReadableText() {
        assertEquals(
            "Heading\n\n• First\n• Second\n\nRead (https://example.com)",
            cleanPluginDescription(
                "<h2>Heading</h2><ul><li>First</li><li>Second</li></ul>" +
                    "<p><a href=\"https://example.com\">Read</a></p>",
            ),
        )
    }

    @Test
    fun namedEntitiesBecomeReadableTextWithoutHtmlTags() {
        assertEquals(
            "Ready. It's working, now",
            cleanPluginDescription("Ready&period; It&apos;s working&comma; now"),
        )
    }
}
