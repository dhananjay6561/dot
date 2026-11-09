package com.dj.dot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DotPatternTest {
    private fun pattern(seed: Int, n: Int = 7) = (0 until n).map { row -> (0 until n).map { col -> patternLit(seed, col, row, n) } }

    @Test fun `mirrors left to right`() {
        val p = pattern("Chaleya".hashCode())
        p.forEach { row -> assertEquals(row, row.reversed()) }
    }

    @Test fun `is the same every time for a title`() = assertEquals(pattern("295".hashCode()), pattern("295".hashCode()))

    @Test fun `differs for titles one letter apart`() = assertNotEquals(pattern("5".hashCode()), pattern("6".hashCode()))
}
