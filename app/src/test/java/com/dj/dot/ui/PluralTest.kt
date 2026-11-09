package com.dj.dot.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralTest {
    @Test fun `uses the single word for one`() = assertEquals("1 song", countOf(1, "song"))

    @Test fun `adds an s for everything else`() {
        assertEquals("0 songs", countOf(0, "song"))
        assertEquals("12 songs", countOf(12, "song"))
    }

    @Test fun `takes an irregular plural`() = assertEquals("3 matches", countOf(3, "match", "matches"))
}
