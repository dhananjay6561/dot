package com.dj.dot.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDataTest {
    private fun song(id: Long, title: String) =
        Song(id, title, "Unknown artist", "", albumId = id, duration = 1_000, track = 0, year = 0, path = "/m/$id.mp3", folder = "/m", dateAdded = 0)

    @Test fun `lists numbered titles after the alphabet`() {
        val lib = LibraryData.build(listOf(song(1, "4"), song(2, "Chaleya"), song(3, "295"), song(4, "ae dil hai mushkil")))
        assertEquals(listOf("ae dil hai mushkil", "Chaleya", "295", "4"), lib.songs.map { it.title })
    }

    @Test fun `ignores leading spaces when grouping`() {
        assertEquals(true, LibraryData.startsWithLetter("  Zara"))
        assertEquals(false, LibraryData.startsWithLetter(" 7"))
    }

    @Test fun `treats an empty title as the # group`() {
        val lib = LibraryData.build(listOf(song(1, ""), song(2, "Zara")))
        assertEquals(listOf("Zara", ""), lib.songs.map { it.title })
    }
}
