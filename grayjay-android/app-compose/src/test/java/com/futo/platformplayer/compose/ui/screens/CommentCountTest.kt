package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentCountTest {
    @Test
    fun firstPageSizeIsNotPresentedAsTotalWhenMoreCommentsExist() {
        assertNull(exactCommentCount(loadedCount = 20, hasMore = true))
    }

    @Test
    fun loadedCountIsPresentedAfterTheFinalPage() {
        assertEquals(47, exactCommentCount(loadedCount = 47, hasMore = false))
    }

    @Test
    fun emptyCommentsKeepThePlainLabel() {
        assertNull(exactCommentCount(loadedCount = 0, hasMore = false))
    }
}
