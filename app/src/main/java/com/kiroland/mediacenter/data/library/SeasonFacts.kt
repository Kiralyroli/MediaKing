package com.kiroland.mediacenter.data.library

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.kiroland.mediacenter.util.AppLocale

/** One season as TMDB lists it: its number, episode count and first air date ("2023-05-04"). */
data class SeasonInfo(val number: Int, val name: String?, val episodeCount: Int, val airDate: String?)

enum class SeasonState { AIRED, AIRING, ANNOUNCED }

/** A series at a glance: aired seasons and episodes, whether it goes on, and what comes next. */
data class SeriesFacts(
    val seasons: List<Pair<SeasonInfo, SeasonState>>,
    val airedSeasons: Int,
    val airedEpisodes: Int,
    val status: String?,
    /** The next episode's air date, when TMDB knows one. */
    val nextAirDate: String?,
)

object SeasonFacts {

    /**
     * [today] as "yyyy-MM-dd". A season with no date or a date after today is announced; one that has
     * started and still has its next episode to come is airing. Specials (season 0) are left out.
     */
    fun build(
        seasons: List<SeasonInfo>,
        tmdbStatus: String?,
        nextEpisodeSeason: Int?,
        nextAirDate: String?,
        today: String,
    ): SeriesFacts {
        val regular = seasons.filter { it.number > 0 }.sortedBy { it.number }
        val withState = regular.map { season ->
            val state = when {
                season.airDate == null || season.airDate > today -> SeasonState.ANNOUNCED
                season.number == nextEpisodeSeason && nextAirDate != null && nextAirDate > today -> SeasonState.AIRING
                else -> SeasonState.AIRED
            }
            season to state
        }
        val aired = withState.filter { it.second != SeasonState.ANNOUNCED }
        return SeriesFacts(
            seasons = withState,
            airedSeasons = aired.size,
            airedEpisodes = aired.sumOf { it.first.episodeCount },
            status = tmdbStatus,
            nextAirDate = nextAirDate?.takeIf { it > today },
        )
    }

    /** "2027. július 8." / "July 8, 2027" / "8. Juli 2027" */
    fun longDate(isoDate: String?): String? = format(isoDate, FormatStyle.LONG)

    /** "2027. júl. 8." / "Jul 8, 2027" / "08.07.2027" */
    fun shortDate(isoDate: String?): String? = format(isoDate, FormatStyle.MEDIUM)

    private fun format(isoDate: String?, style: FormatStyle): String? = runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofLocalizedDate(style).withLocale(AppLocale.current))
    }.getOrNull()

    fun today(): String = LocalDate.now().toString()
}
