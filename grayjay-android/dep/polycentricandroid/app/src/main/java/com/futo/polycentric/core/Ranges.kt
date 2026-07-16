package com.futo.polycentric.core

class Ranges {
    companion object {
        fun insert(ranges: MutableList<Range>, item: Long) {
            for (i in ranges.indices) {
                // within existing range
                if (item >= ranges[i].low && item <= ranges[i].high) {
                    return
                }

                // merging range
                if (i < ranges.size - 1 &&
                    item == ranges[i].high + 1L &&
                    item == ranges[i + 1].low - 1L) {
                    ranges[i] = Range(ranges[i].low, ranges[i + 1].high)
                    ranges.removeAt(i + 1)
                    return
                }

                // low adjacent
                if (item == ranges[i].low - 1L) {
                    ranges[i] = Range(item, ranges[i].high)
                    return
                }

                // high adjacent
                if (item == ranges[i].high + 1L) {
                    ranges[i] = Range(ranges[i].low, item)
                    return
                }

                // between ranges and non adjacent
                if (item < ranges[i].low) {
                    ranges.add(i, Range(item, item))
                    return
                }
            }

            // greater than everything
            ranges.add(Range(item, item))
        }

        fun subtract(left: List<Range>, right: List<Range>): List<Range> {
            val result = left.map { Range(it.low, it.high) }.toMutableList()

            for (range in right) {
                var i = result.lastIndex
                while (i >= 0) {
                    val item = result[i]

                    if (range.high < item.low || range.low > item.high) {
                        i--
                        continue
                    }

                    if (range.low <= item.low && range.high >= item.high) {
                        result.removeAt(i)
                    } else if (range.low <= item.low) {
                        result[i] = Range(range.high + 1L, item.high)
                    } else if (range.high >= item.high) {
                        result[i] = Range(item.low, range.low - 1L)
                    } else if (range.low > item.low && range.high < item.high) {
                        val current = result.removeAt(i)

                        result.add(Range(current.low, range.low - 1L))
                        result.add(Range(range.high + 1L, current.high))
                    } else {
                        throw Exception("Impossible")
                    }

                    i--
                }
            }

            return result
        }

        fun takeRangesMaxItems(ranges: List<Range>, limit: Long): List<Range> {
            var sum = 0L
            val result = mutableListOf<Range>()

            if (limit == 0L) {
                return emptyList()
            }

            for (range in ranges) {
                val count = range.high - range.low + 1L
                val maxItems = limit - sum

                if (count <= maxItems) {
                    result.add(range)
                    sum += count
                } else {
                    result.add(Range(range.low, range.low + maxItems - 1L))
                    break
                }
            }

            return result
        }
    }
}