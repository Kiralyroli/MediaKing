package com.kiroland.mediacenter.data.library

import com.kiroland.mediacenter.data.library.db.FinishedEpisode
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class WatchStatsTest {
    private fun at(date: String) = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun title(id: Int, movie: Boolean, date: String, rating: Int? = null) =
        WatchedTitleEntity(WatchedRepository.key(movie, id), movie, id, "T$id", null, null, at(date), rating)

    @Test
    fun `counts, hours, months, genres and ratings`() {
        val titles = listOf(
            title(1, true, "2026-10-01", 5),
            title(2, true, "2026-03-15", 4),
            title(3, false, "2026-10-03", 5),
            title(4, true, "2025-12-24"),
        )
        val details = mapOf(
            "movie:1" to TitleDetails(listOf("Horror", "Thriller"), 100),
            "movie:2" to TitleDetails(listOf("Horror"), 80),
            "tv:3" to TitleDetails(listOf("Drama"), null),
            "movie:4" to TitleDetails(listOf("Family"), 90),
        )
        // Two 40-minute episodes this year, one last year.
        val episodes = listOf(FinishedEpisode(40 * 60_000L, at("2026-05-01")), FinishedEpisode(40 * 60_000L, at("2026-06-01")), FinishedEpisode(40 * 60_000L, at("2025-06-01")))

        val stats = WatchStats.compute(titles, details, episodes, now = at("2026-10-06"), zone = ZoneOffset.UTC)

        assertEquals(2, stats.moviesThisYear)
        assertEquals(1, stats.seriesThisYear)
        assertEquals(4, stats.total)
        assertEquals(14.0 / 3, stats.averageRating!!, 0.001)
        assertEquals((100 + 80 + 80) / 60, stats.hoursThisYear)
        assertEquals(12, stats.perMonth.size)
        assertEquals(YearMonth.of(2026, 10) to 2, stats.perMonth.last())
        assertEquals(YearMonth.of(2025, 11) to 0, stats.perMonth.first())
        assertEquals(1, stats.perMonth.first { it.first == YearMonth.of(2025, 12) }.second)
        assertEquals("Horror" to 2, stats.topGenres.first())
        assertEquals(listOf(5 to 2, 4 to 1, 3 to 0, 2 to 0, 1 to 0), stats.ratings)
    }
}
