package com.futo.polycentric.core

import org.junit.Test
import org.junit.Assert

class RangesTest {
    @Test
    fun insertSingleton() {
        var ranges = mutableListOf<Range>()
        Ranges.insert(ranges, 5L)
        Assert.assertEquals(ranges, mutableListOf(Range(5L, 5L)))
    }

    @Test
    fun insertSequential() {
        var ranges = mutableListOf<Range>()
        Ranges.insert(ranges, 5L)
        Ranges.insert(ranges, 6L)
        Assert.assertEquals(ranges, mutableListOf(Range(5L, 6L)))
    }

    @Test
    fun insertMerge() {
        var ranges = mutableListOf<Range>()
        Ranges.insert(ranges, 5L)
        Ranges.insert(ranges, 7L)
        Ranges.insert(ranges, 6L)
        Assert.assertEquals(ranges, mutableListOf(Range(5L, 7L)))
    }
    @Test
    fun insertDisconnected() {
        var ranges = mutableListOf<Range>()
        Ranges.insert(ranges, 1L)
        Ranges.insert(ranges, 5L)
        Ranges.insert(ranges, 3L)
        Assert.assertEquals(ranges, mutableListOf(Range(1L, 1L), Range(3L, 3L), Range(5L, 5L)))
    }

    @Test
    fun insertNonAdjacentLessThanSingleItem() {
        var ranges = mutableListOf<Range>()
        Ranges.insert(ranges, 10L)
        Ranges.insert(ranges, 5L)
        Assert.assertEquals(ranges, mutableListOf(Range(5L, 5L), Range(10L, 10L)))
    }
}
