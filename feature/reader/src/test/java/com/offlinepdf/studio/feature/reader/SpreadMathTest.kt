package com.offlinepdf.studio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadMathTest {
    @Test
    fun coverIsAlone_thenPairs() {
        assertEquals(listOf(0), SpreadMath.pagesOf(0, 10))
        assertEquals(listOf(1, 2), SpreadMath.pagesOf(1, 10))
        assertEquals(listOf(3, 4), SpreadMath.pagesOf(2, 10))
    }

    @Test
    fun lastSpreadMayBeSingle() {
        // 4 pages: [0] [1,2] [3]
        assertEquals(3, SpreadMath.spreadCount(4))
        assertEquals(listOf(3), SpreadMath.pagesOf(2, 4))
    }

    @Test
    fun spreadCountMatchesAllPages() {
        for (n in 1..40) {
            val covered = (0 until SpreadMath.spreadCount(n)).flatMap { SpreadMath.pagesOf(it, n) }
            assertEquals((0 until n).toList(), covered)
        }
    }

    @Test
    fun pageToSpreadRoundTrips() {
        for (page in 0..50) {
            val spread = SpreadMath.spreadOf(page)
            assert(page in SpreadMath.pagesOf(spread, 100))
            assertEquals(SpreadMath.firstPageOf(spread), SpreadMath.pagesOf(spread, 100).first())
        }
    }
}
