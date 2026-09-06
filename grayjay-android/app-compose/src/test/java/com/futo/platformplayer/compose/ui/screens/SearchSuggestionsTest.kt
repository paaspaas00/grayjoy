package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSuggestionsTest {
    @Test
    fun suggestionsFromDifferentSourcesCannotCreateDuplicateLazyKeys() {
        val result = uniqueSearchSuggestions(listOf("Android", "android", " Android ", "", "  ", "Linux"))
        assertEquals(listOf("Android", "Linux"), result)
        assertEquals(result.size, result.map { it.lowercase() }.distinct().size)
    }
}
