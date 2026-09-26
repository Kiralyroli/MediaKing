package com.kiroland.mediacenter.data.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {

    @Test
    fun `accents and case do not matter`() {
        assertTrue(SearchMatcher.matches("vegso", listOf("Végső állomás")))
        assertTrue(SearchMatcher.matches("BŰBÁJOS", listOf("Bűbájos boszorkák")))
        assertTrue(SearchMatcher.matches("arvizturo", listOf("ÁRVÍZTŰRŐ tükörfúrógép")))
    }

    @Test
    fun `every word must appear somewhere`() {
        val texts = listOf("Élet", "Life", "life.2160p.remux-trinity.mkv")
        assertTrue(SearchMatcher.matches("life", texts))
        assertTrue(SearchMatcher.matches("elet remux", texts))
        assertFalse(SearchMatcher.matches("life moon", texts))
    }

    @Test
    fun `punctuation is ignored and blank queries match nothing`() {
        assertTrue(SearchMatcher.matches("dead city", listOf("The Walking Dead: Dead City")))
        assertFalse(SearchMatcher.matches("   ", listOf("anything")))
    }
}
