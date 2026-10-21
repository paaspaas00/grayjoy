package com.futo.platformplayer.compose.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceBridgeTest {
    @Test fun framesAreBoundedAndTruncatedSessionsAreSafe() {
        assertEquals("abc", readBoundedLine("abc\nignored".reader()))
        assertNull(readBoundedLine("".reader()))
        assertTrue(runCatching { readBoundedLine("""{"method":"tools/call"}""".reader()) }.isFailure)
        repeat(128) { size ->
            val frame = "x".repeat(size)
            assertEquals(frame, readBoundedLine((frame + "\n").reader(), size))
            if (size > 0) assertTrue(runCatching { readBoundedLine(frame.reader(), size) }.isFailure)
            assertTrue(runCatching { readBoundedLine((frame + "x\n").reader(), size) }.isFailure)
        }
        assertTrue(runCatching { readBoundedLine("x".repeat(17_000).reader()) }.isFailure)
    }
    @Test fun mcpToolsDoNotExposeDataMutationOrArbitraryUrls() {
        val tools = voiceTools()
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertEquals(names.size, names.distinct().size)
        assertTrue("search_play" in names)
        assertFalse(names.any { it.contains("delete") || it.contains("import") || it.contains("profile") })
    }
}
