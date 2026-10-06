package com.kiroland.mediacenter.data.library

import com.kiroland.mediacenter.data.library.db.FinishedEpisode
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Genres and runtime of a watched title, from TMDB. */
data class TitleDetails(val genres: List<String>, val runtimeMinutes: Int?)

data class WatchStats(
    val moviesThisYear: Int,
    val seriesThisYear: Int,
    val total: Int,
    /** null without ratings. */
    val averageRating: Double?,
    /** Films' runtimes plus library episodes watched to the end, this year. */
    val hoursThisYear: Int,
    /** The last 12 months, oldest first: month and titles marked watched in it. */
    val perMonth: List<Pair<YearMonth, Int>>,
    /** Most frequent genres, most first (at most 6). */
    val topGenres: List<Pair<String, Int>>,
    /** Ratings 5..1 and how many titles got each. */
    val ratings: List<Pair<Int, Int>>,
) {
    companion object {
        /** Pure, so it is unit tested; [details] by watched key ("movie:157336"). */
        fun compute(
            titles: List<WatchedTitleEntity>,
            details: Map<String, TitleDetails>,
            episodes: List<FinishedEpisode>,
            now: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): WatchStats {
            fun month(at: Long) = YearMonth.from(Instant.ofEpochMilli(at).atZone(zone))
            val thisMonth = month(now)
            val thisYear = thisMonth.year
            val inYear = titles.filter { month(it.watchedAt).year == thisYear }

            val movieMinutes = inYear.filter { it.isMovie }.sumOf { details[it.key]?.runtimeMinutes ?: 0 }
            val episodeMinutes = episodes.filter { month(it.updatedAt).year == thisYear }.sumOf { it.durationMs / 60_000 }

            val months = (11 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
            val counts = titles.groupingBy { month(it.watchedAt) }.eachCount()

            val rated = titles.mapNotNull { it.rating }
            return WatchStats(
                moviesThisYear = inYear.count { it.isMovie },
                seriesThisYear = inYear.count { !it.isMovie },
                total = titles.size,
                averageRating = rated.takeIf { it.isNotEmpty() }?.average(),
                hoursThisYear = ((movieMinutes + episodeMinutes) / 60).toInt(),
                perMonth = months.map { it to (counts[it] ?: 0) },
                topGenres = titles.flatMap { details[it.key]?.genres.orEmpty() }
                    .groupingBy { it }.eachCount()
                    .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .take(6).map { it.key to it.value },
                ratings = (5 downTo 1).map { star -> star to rated.count { it == star } },
            )
        }
    }
}
