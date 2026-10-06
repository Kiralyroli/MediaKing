package com.kiroland.mediacenter.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `newest first, no duplicates, at most ten`() {
        assertEquals(listOf("silo", "dune"), SearchHistory.remember(listOf("dune"), "silo"))
        assertEquals(listOf("Dune", "silo"), SearchHistory.remember(listOf("silo", "dune"), "Dune"))
        val full = (1..10).map { "q$it" }
        val next = SearchHistory.remember(full, "new")
        assertEquals(10, next.size)
        assertEquals("new", next.first())
        assertEquals("q9", next.last())
    }
}
