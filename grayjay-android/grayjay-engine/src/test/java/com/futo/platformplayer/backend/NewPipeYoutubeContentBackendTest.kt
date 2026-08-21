package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.Description

class NewPipeYoutubeContentBackendTest {
    @Test
    fun htmlDescriptionIsPresentedAsReadableText() {
        val result = cleanNewPipeDescription(
            Description(
                "<p>Hello <b>world</b><br>Second line</p>" +
                    "<ul><li>First</li><li>Second</li></ul>" +
                    "<p><a href=\"https://example.com\">Details</a></p>",
                Description.HTML,
            ),
        )

        assertFalse(result.contains('<'))
        assertTrue(result.contains("Hello world"))
        assertTrue(result.contains("Second line"))
        assertTrue(result.contains("• First"))
        assertTrue(result.contains("Details (https://example.com)"))
    }

    @Test
    fun plainTextIsNotReinterpretedAsHtml() {
        assertEquals("2 < 3 and 5 > 4", cleanNewPipeText("2 < 3 and 5 > 4"))
    }
}
