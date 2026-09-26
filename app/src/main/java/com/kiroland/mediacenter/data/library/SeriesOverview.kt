package com.kiroland.mediacenter.data.library

import com.kiroland.mediacenter.data.library.db.EpisodeMetadataEntity
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.library.db.SeasonMetadataEntity

/** One line of a season: a file in the library, or an episode TMDB knows of that is not there. */
sealed interface EpisodeRow {
    val episode: Int?

    data class Owned(val item: MediaWithProgress) : EpisodeRow {
        override val episode: Int? get() = item.media.episode
    }

    data class Missing(val season: Int, override val episode: Int, val info: EpisodeMetadataEntity?) : EpisodeRow {
        /** "2026-10-05"; not yet aired if after today. */
        val airDate: String? get() = info?.airDate
    }
}

data class SeasonOverview(
    val season: Int,
    val name: String?,
    val rows: List<EpisodeRow>,
    /** Episodes TMDB lists for the season, if known. */
    val listedEpisodes: Int?,
) {
    val ownedCount: Int get() = rows.count { it is EpisodeRow.Owned }
    val isMissing: Boolean get() = ownedCount == 0
}

/** Merges the library's files with TMDB's seasons and episodes, so gaps and later seasons show. */
object SeriesOverview {

    fun build(
        files: List<MediaWithProgress>,
        seasons: List<SeasonMetadataEntity>,
        episodes: List<EpisodeMetadataEntity>,
    ): List<SeasonOverview> {
        val filesBySeason = files.groupBy { it.media.season ?: 0 }
        val listed = seasons.associateBy { it.season }
        val episodesBySeason = episodes.groupBy { it.season }
        // Specials (season 0) only when there is one in the library: TMDB lists plenty nobody has.
        val numbers = (filesBySeason.keys + listed.keys.filter { it > 0 }).toSortedSet()

        return numbers.map { season ->
            val owned = filesBySeason[season].orEmpty()
            val covered = owned.flatMapTo(HashSet()) { f ->
                val first = f.media.episode ?: return@flatMapTo emptyList()
                (first..(f.media.episodeEnd ?: first)).toList()
            }
            val known = episodesBySeason[season].orEmpty().associateBy { it.episode }
            val listedCount = listed[season]?.episodeCount
            // Before the episode list loads, the count alone still shows how many are missing.
            val expected = (known.keys + (1..(listedCount ?: 0))).toSortedSet()
            val missing = expected.filterNot { it in covered }.map { EpisodeRow.Missing(season, it, known[it]) }
            val rows = (owned.map { EpisodeRow.Owned(it) } + missing)
                .sortedWith(compareBy<EpisodeRow> { it.episode ?: Int.MAX_VALUE }.thenBy { it is EpisodeRow.Missing })
            SeasonOverview(season, listed[season]?.name, rows, listedCount ?: known.size.takeIf { it > 0 })
        }
    }
}
