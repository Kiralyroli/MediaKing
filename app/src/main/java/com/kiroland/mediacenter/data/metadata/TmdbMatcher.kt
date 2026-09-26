package com.kiroland.mediacenter.data.metadata

import com.kiroland.mediacenter.media.parse.MediaNameParser
import kotlin.math.abs
import kotlin.math.ln

/** A search hit reduced to what matching needs. */
data class Candidate(val id: Int, val titles: List<String?>, val year: Int?, val popularity: Double)

/**
 * Picks the best search result. TMDB's own order is not good enough: "Life" (2017) returns
 * "No Game No Life: Zero" first. An exact title match (Hungarian or original) beats everything,
 * then the year, then popularity breaks ties.
 */
object TmdbMatcher {

    fun best(query: String, year: Int?, candidates: List<Candidate>): Candidate? {
        val wanted = MediaNameParser.seriesKey(query)
        return candidates
            .mapNotNull { candidate -> score(wanted, year, candidate)?.let { candidate to it } }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    /** null when the title has nothing to do with the query: a matching year alone is no match. */
    private fun score(wanted: String, year: Int?, candidate: Candidate): Double? {
        val keys = candidate.titles.filterNotNull().map(MediaNameParser::seriesKey).filter { it.isNotEmpty() }
        val titleScore = when {
            wanted in keys -> 100.0
            keys.any { it.startsWith(wanted) || wanted.startsWith(it) } -> 30.0
            else -> return null
        }
        val yearScore = when {
            year == null || candidate.year == null -> 0.0
            candidate.year == year -> 40.0
            abs(candidate.year - year) == 1 -> 25.0 // Festival vs. release year.
            else -> -20.0
        }
        // log keeps popularity a tie-breaker rather than the deciding factor.
        return titleScore + yearScore + ln(1 + candidate.popularity)
    }

    fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()
}
