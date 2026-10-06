package com.kiroland.mediacenter.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonFactsTest {

    // Silo as TMDB lists it on 2026-10-05.
    private val silo = listOf(
        SeasonInfo(1, "1. évad", 10, "2023-05-04"),
        SeasonInfo(2, "2. évad", 10, "2024-11-14"),
        SeasonInfo(3, "3. évad", 10, "2026-07-02"),
        SeasonInfo(4, "4. évad", 1, "2027-07-08"),
    )

    @Test
    fun `an announced season is told apart and not counted`() {
        val facts = SeasonFacts.build(silo, "Returning Series", nextEpisodeSeason = 4, nextAirDate = "2027-07-08", today = "2026-10-05")
        assertEquals(listOf(SeasonState.AIRED, SeasonState.AIRED, SeasonState.AIRED, SeasonState.ANNOUNCED), facts.seasons.map { it.second })
        assertEquals(3, facts.airedSeasons)
        assertEquals(30, facts.airedEpisodes)
        assertEquals("Returning Series", facts.status)
        assertEquals("2027-07-08", facts.nextAirDate)
    }

    @Test
    fun `a season with its next episode still to come is airing`() {
        val facts = SeasonFacts.build(silo.take(3), "Returning Series", nextEpisodeSeason = 3, nextAirDate = "2026-08-20", today = "2026-08-01")
        assertEquals(SeasonState.AIRING, facts.seasons.last().second)
        assertEquals(3, facts.airedSeasons)
    }

    @Test
    fun `specials and dateless seasons`() {
        val facts = SeasonFacts.build(
            listOf(SeasonInfo(0, "Különkiadások", 3, "2020-01-01"), SeasonInfo(1, "1. évad", 8, "2020-02-01"), SeasonInfo(2, "2. évad", 0, null)),
            "Ended", nextEpisodeSeason = null, nextAirDate = null, today = "2026-10-05",
        )
        assertEquals(listOf(1, 2), facts.seasons.map { it.first.number })
        assertEquals(SeasonState.ANNOUNCED, facts.seasons.last().second)
        assertEquals("Ended", facts.status)
        assertNull(facts.nextAirDate)
    }

    @Test
    fun `hungarian dates`() {
        assertEquals("2027. július 8.", SeasonFacts.longDate("2027-07-08"))
        assertNull(SeasonFacts.longDate(null))
    }
}
