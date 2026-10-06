package com.kiroland.mediacenter.data.news

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsDetectorTest {
    private val silo = TitleState(providers = setOf(350), airedSeasons = 3, announcedSeason = 4, announcedDate = null)

    @Test
    fun `first check is only the baseline`() {
        assertEquals(emptyList<NewsEvent>(), NewsDetector.detect(null, silo))
    }

    @Test
    fun `nothing changed`() {
        assertEquals(emptyList<NewsEvent>(), NewsDetector.detect(silo, silo))
    }

    @Test
    fun `new service`() {
        assertEquals(
            listOf(NewsEvent(NewsKind.AVAILABLE, providerId = 8)),
            NewsDetector.detect(silo, silo.copy(providers = setOf(350, 8))),
        )
        // Leaving a service is not news.
        assertEquals(emptyList<NewsEvent>(), NewsDetector.detect(silo, silo.copy(providers = emptySet())))
    }

    @Test
    fun `announced season gets its date`() {
        assertEquals(
            listOf(NewsEvent(NewsKind.SEASON_ANNOUNCED, season = 4, date = "2027-07-08")),
            NewsDetector.detect(silo, silo.copy(announcedDate = "2027-07-08")),
        )
    }

    @Test
    fun `season starts and the next one is announced`() {
        val now = silo.copy(airedSeasons = 4, announcedSeason = 5, announcedDate = null)
        assertEquals(
            listOf(NewsEvent(NewsKind.SEASON_OUT, season = 4), NewsEvent(NewsKind.SEASON_ANNOUNCED, season = 5)),
            NewsDetector.detect(silo, now),
        )
    }

    @Test
    fun `services are not compared when not asked`() {
        val library = silo.copy(providers = null)
        assertEquals(emptyList<NewsEvent>(), NewsDetector.detect(library, silo.copy(providers = setOf(8))))
    }
}
