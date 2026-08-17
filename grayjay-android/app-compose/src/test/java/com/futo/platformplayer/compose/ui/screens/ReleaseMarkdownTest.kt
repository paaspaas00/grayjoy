package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMarkdownTest {
    @Test
    fun `release markdown separates headings lists and paragraphs`() {
        val blocks = parseReleaseMarkdown(
            """
            ## Changes

            - First fix
            - Second fix

            A final paragraph
            continuing here.
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertEquals(ReleaseMarkdownBlock.Heading(2, "Changes"), blocks[0])
        assertTrue(blocks[1] is ReleaseMarkdownBlock.Bullet)
        assertTrue(blocks[2] is ReleaseMarkdownBlock.Bullet)
        assertEquals(
            ReleaseMarkdownBlock.Paragraph("A final paragraph continuing here."),
            blocks[3],
        )
    }
}
