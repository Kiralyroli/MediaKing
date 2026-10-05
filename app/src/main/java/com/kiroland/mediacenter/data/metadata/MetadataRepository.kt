package com.kiroland.mediacenter.data.metadata

import android.util.Log
import com.kiroland.mediacenter.data.library.db.EpisodeMetadataEntity
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MetadataDao
import com.kiroland.mediacenter.data.library.db.MetadataEntity
import com.kiroland.mediacenter.data.library.db.SeasonMetadataEntity
import com.kiroland.mediacenter.data.library.SeasonFacts
import com.kiroland.mediacenter.data.library.SeasonInfo
import com.kiroland.mediacenter.data.library.SeriesFacts
import com.kiroland.mediacenter.data.metadata.tmdb.MovieDetails
import com.kiroland.mediacenter.data.metadata.tmdb.SeasonSummary
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbApi
import com.kiroland.mediacenter.data.metadata.tmdb.TvDetails
import com.kiroland.mediacenter.media.parse.MediaNameParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Fills in TMDB data for library items that have none yet. Failures are logged and retried next time. */
@Singleton
class MetadataRepository @Inject constructor(
    private val dao: MetadataDao,
    private val api: TmdbApi,
    private val credentials: TmdbCredentials,
) {
    val isConfigured: Boolean get() = credentials.isConfigured

    fun episodes(tvId: Int): Flow<List<EpisodeMetadataEntity>> = dao.observeEpisodes(tvId)

    fun seasons(tvId: Int): Flow<List<SeasonMetadataEntity>> = dao.observeSeasons(tvId)

    suspend fun enrichMissing() {
        if (!isConfigured) return
        val retryAfter = System.currentTimeMillis() - NOT_FOUND_RETRY_MS
        for (key in dao.keysToFetch(retryAfter)) {
            runLogged("lookup $key") { lookup(key) }
        }
        // New seasons and episodes appear while a show runs: re-read the season lists weekly.
        for (tvId in dao.showsToRefresh(System.currentTimeMillis() - SEASONS_REFRESH_MS)) {
            runLogged("seasons $tvId") { storeSeasons(tvId, api.tv(tvId, append = "").seasons) }
        }
        fetchPendingSeasons()
    }

    /** Episode lists of library seasons and of every season TMDB lists, once each (again when a season grows). */
    private suspend fun fetchPendingSeasons() {
        for (season in (dao.seasonsToFetch() + dao.listedSeasonsToFetch()).distinct()) {
            runLogged("season ${season.tvId}/${season.season}") { fetchSeason(season.tvId, season.season) }
        }
    }

    private suspend fun storeSeasons(tvId: Int, summaries: List<SeasonSummary>) {
        val now = System.currentTimeMillis()
        val known = dao.seasons(tvId).associateBy { it.season }
        dao.upsertSeasons(
            summaries.map { s ->
                val old = known[s.seasonNumber]
                SeasonMetadataEntity(
                    tvId = tvId,
                    season = s.seasonNumber,
                    name = s.name,
                    episodeCount = s.episodeCount,
                    airDate = s.airDate,
                    fetchedAt = now,
                    // A season that grew (still airing) gets its episode list reloaded.
                    episodesFetchedAt = old?.episodesFetchedAt?.takeIf { old.episodeCount == s.episodeCount },
                )
            },
        )
        dao.deleteSeasonsExcept(tvId, summaries.map { it.seasonNumber })
    }

    /** TMDB search for the "wrong match?" screen; the user picks the right one. */
    suspend fun search(kind: MediaKind, query: String): List<MatchCandidate> =
        if (kind == MediaKind.MOVIE) {
            api.searchMovie(query, null).results.map {
                MatchCandidate(it.id, it.title ?: it.originalTitle.orEmpty(), it.originalTitle, TmdbMatcher.yearOf(it.releaseDate), it.posterPath, it.overview)
            }
        } else {
            api.searchTv(query, null).results.map {
                MatchCandidate(it.id, it.name ?: it.originalName.orEmpty(), it.originalName, TmdbMatcher.yearOf(it.firstAirDate), it.posterPath, it.overview)
            }
        }

    /** Replaces the match for [key]; kept until the user reloads all metadata. Episode data follows. */
    suspend fun applyMatch(key: String, kind: MediaKind, tmdbId: Int) {
        dao.upsert(if (kind == MediaKind.MOVIE) movieEntity(key, tmdbId) else showEntity(key, tmdbId))
        if (kind == MediaKind.EPISODE) fetchPendingSeasons()
    }

    /** Forgets every TMDB match (including manual ones) and looks everything up again. */
    suspend fun reloadAll() {
        dao.clearEpisodes()
        dao.clearSeasons()
        dao.clearMetadata()
        enrichMissing()
    }

    private suspend fun lookup(key: String) {
        val inputs = dao.lookupInputs(key)
        val title = inputs.firstOrNull()?.title ?: return
        // Several files may carry different years (or none); take the most common one.
        val year = inputs.mapNotNull { it.year }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val metadata = when (inputs.first().kind) {
            MediaKind.MOVIE -> findMovie(key, title, year)
            MediaKind.EPISODE -> findShow(key, title, year)
        }
        dao.upsert(metadata ?: notFound(key))
    }

    private suspend fun findMovie(key: String, title: String, year: Int?): MetadataEntity? {
        var results = api.searchMovie(title, year).results
        if (results.isEmpty() && year != null) results = api.searchMovie(title, null).results
        val candidates = results.map {
            Candidate(it.id, listOf(it.title, it.originalTitle), TmdbMatcher.yearOf(it.releaseDate), it.popularity)
        }
        val match = TmdbMatcher.best(title, year, candidates) ?: return null
        return movieEntity(key, match.id)
    }

    private suspend fun movieEntity(key: String, id: Int): MetadataEntity {
        val details = api.movie(id)
        val overview = details.overview?.takeIf { it.isNotBlank() }
            ?: api.movie(id, TmdbApi.FALLBACK_LANGUAGE, append = "").overview
        return details.toEntity(key, overview)
    }

    private suspend fun findShow(key: String, title: String, year: Int?): MetadataEntity? {
        var results = api.searchTv(title, year).results
        if (results.isEmpty() && year != null) results = api.searchTv(title, null).results
        val candidates = results.map {
            Candidate(it.id, listOf(it.name, it.originalName), TmdbMatcher.yearOf(it.firstAirDate), it.popularity)
        }
        val match = TmdbMatcher.best(title, year, candidates) ?: return null
        return showEntity(key, match.id)
    }

    /** Seasons, episode counts, air dates and what comes next, fresh from TMDB (series change often). */
    suspend fun seriesFacts(tvId: Int): SeriesFacts {
        val details = api.tv(tvId, append = "")
        return SeasonFacts.build(
            seasons = details.seasons.map { SeasonInfo(it.seasonNumber, it.name, it.episodeCount, it.airDate) },
            tmdbStatus = details.status,
            nextEpisodeSeason = details.nextEpisodeToAir?.seasonNumber,
            nextAirDate = details.nextEpisodeToAir?.airDate,
            today = SeasonFacts.today(),
        )
    }

    /**
     * A title's TMDB data in the library's shape, for titles that are not in the library (streaming
     * search); nothing is stored.
     */
    suspend fun preview(isMovie: Boolean, id: Int): MetadataEntity {
        val key = (if (isMovie) "preview:movie:" else "preview:tv:") + id
        if (isMovie) return movieEntity(key, id)
        val details = api.tv(id)
        val overview = details.overview?.takeIf { it.isNotBlank() }
            ?: api.tv(id, TmdbApi.FALLBACK_LANGUAGE, append = "").overview
        return details.toEntity(key, overview)
    }

    private suspend fun showEntity(key: String, id: Int): MetadataEntity {
        val details = api.tv(id)
        val overview = details.overview?.takeIf { it.isNotBlank() }
            ?: api.tv(id, TmdbApi.FALLBACK_LANGUAGE, append = "").overview
        storeSeasons(id, details.seasons)
        return details.toEntity(key, overview)
    }

    private suspend fun fetchSeason(tvId: Int, season: Int) {
        val episodes = api.season(tvId, season).episodes
        // Hungarian episode texts are often missing; fill the gaps from English in one extra call.
        val english = if (episodes.any { it.overview.isNullOrBlank() || it.name.isNullOrBlank() }) {
            api.season(tvId, season, TmdbApi.FALLBACK_LANGUAGE).episodes.associateBy { it.episodeNumber }
        } else {
            emptyMap()
        }
        dao.upsertEpisodes(
            episodes.map { ep ->
                val en = english[ep.episodeNumber]
                EpisodeMetadataEntity(
                    tvId = tvId,
                    season = ep.seasonNumber,
                    episode = ep.episodeNumber,
                    name = ep.name?.takeUnless { it.isBlank() || GENERIC_EPISODE_NAME.matches(it) } ?: en?.name,
                    overview = ep.overview?.takeIf { it.isNotBlank() } ?: en?.overview,
                    stillPath = ep.stillPath,
                    airDate = ep.airDate,
                    runtimeMinutes = ep.runtime,
                )
            },
        )
        dao.markEpisodesFetched(tvId, season, System.currentTimeMillis())
    }

    private suspend fun runLogged(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "TMDB $what failed: ${e.message}")
        }
    }

    private fun notFound(key: String) = MetadataEntity(
        key = key, tmdbId = null, title = null, originalTitle = null, overview = null, posterPath = null,
        backdropPath = null, year = null, rating = null, genres = null, runtimeMinutes = null, director = null,
        cast = null, fetchedAt = System.currentTimeMillis(),
    )

    private fun MovieDetails.toEntity(key: String, overview: String?) = MetadataEntity(
        key = key,
        tmdbId = id,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = TmdbMatcher.yearOf(releaseDate),
        rating = voteAverage?.takeIf { it > 0 },
        genres = genres.joinToString(", ") { it.name }.ifBlank { null },
        runtimeMinutes = runtime?.takeIf { it > 0 },
        director = credits?.crew?.filter { it.job == "Director" }?.joinToString(", ") { it.name }?.ifBlank { null },
        cast = credits?.cast?.sortedBy { it.order }?.take(CAST_SIZE)?.joinToString(", ") { it.name }?.ifBlank { null },
        fetchedAt = System.currentTimeMillis(),
    )

    private fun TvDetails.toEntity(key: String, overview: String?) = MetadataEntity(
        key = key,
        tmdbId = id,
        title = name,
        originalTitle = originalName,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = TmdbMatcher.yearOf(firstAirDate),
        rating = voteAverage?.takeIf { it > 0 },
        genres = genres.joinToString(", ") { it.name }.ifBlank { null },
        runtimeMinutes = episodeRunTime.firstOrNull(),
        director = createdBy.joinToString(", ") { it.name }.ifBlank { null },
        cast = credits?.cast?.sortedBy { it.order }?.take(CAST_SIZE)?.joinToString(", ") { it.name }?.ifBlank { null },
        fetchedAt = System.currentTimeMillis(),
    )

    companion object {
        private const val TAG = "MetadataRepository"
        private const val CAST_SIZE = 8
        private const val NOT_FOUND_RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private const val SEASONS_REFRESH_MS = 7L * 24 * 60 * 60 * 1000
        /** TMDB's placeholder names for untranslated episodes: "1. epizód", "Episode 1". */
        private val GENERIC_EPISODE_NAME = Regex("""(?i)^(\d+\.\s*epizód|episode\s*\d+|\d+\.\s*rész)$""")

        fun movieKey(title: String, year: Int?) = "movie:${MediaNameParser.seriesKey(title)}:${year ?: ""}"
        fun showKey(seriesKey: String) = "tv:$seriesKey"
    }
}

/** One search hit for the manual "wrong match?" screen. */
data class MatchCandidate(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val posterPath: String?,
    val overview: String?,
)
