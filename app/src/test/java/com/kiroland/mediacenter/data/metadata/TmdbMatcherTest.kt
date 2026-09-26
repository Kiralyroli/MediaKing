package com.kiroland.mediacenter.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Candidates are real TMDB search results (hu-HU) captured for titles in the user's library. */
class TmdbMatcherTest {

    @Test
    fun `exact original title beats tmdb's first hit`() {
        val results = listOf(
            Candidate(445030, listOf("No Game No Life: Zero", "ノーゲーム・ノーライフ ゼロ"), 2017, 4.9),
            Candidate(395992, listOf("Élet", "Life"), 2017, 13.6),
            Candidate(257440, listOf("Term Life", "Term Life"), 2016, 4.6),
            Candidate(1585, listOf("Az élet csodaszép", "It's a Wonderful Life"), 1946, 18.4),
        )
        assertEquals(395992, TmdbMatcher.best("Life", 2017, results)?.id)
    }

    @Test
    fun `year separates remakes and reboots`() {
        val results = listOf(
            Candidate(71663, listOf("Bűbájos boszorkák", "Charmed"), 2018, 60.0),
            Candidate(1981, listOf("Bűbájos boszorkák", "Charmed"), 1998, 141.3),
        )
        assertEquals(1981, TmdbMatcher.best("Charmed", 1998, results)?.id)
        assertEquals(71663, TmdbMatcher.best("Charmed", 2018, results)?.id)
    }

    @Test
    fun `punctuation and case do not matter`() {
        val results = listOf(
            Candidate(257679, listOf("The Last Drive-in: The Walking Dead - Dead City"), 2023, 1.3),
            Candidate(194583, listOf("The Walking Dead: Dead City"), 2023, 48.4),
        )
        assertEquals(194583, TmdbMatcher.best("The Walking Dead Dead City", null, results)?.id)
    }

    @Test
    fun `popularity decides between equal titles`() {
        val results = listOf(
            Candidate(256215, listOf("Silo"), 2017, 0.88),
            Candidate(125988, listOf("A siló", "Silo"), 2023, 210.4),
        )
        assertEquals(125988, TmdbMatcher.best("Silo", null, results)?.id)
    }

    @Test
    fun `unrelated results are rejected`() {
        val results = listOf(Candidate(1, listOf("Something Else"), 2017, 99.0))
        assertNull(TmdbMatcher.best("Life", 2017, results))
    }
}
