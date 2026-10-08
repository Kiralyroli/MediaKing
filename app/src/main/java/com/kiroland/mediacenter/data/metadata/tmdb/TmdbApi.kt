package com.kiroland.mediacenter.data.metadata.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** The few TMDB v3 endpoints the library needs. Auth is a bearer token added by an OkHttp interceptor. */
interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("year") year: Int?,
        @Query("language") language: String = LANGUAGE,
    ): SearchResponse<MovieResult>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int?,
        @Query("language") language: String = LANGUAGE,
    ): SearchResponse<TvResult>

    /** Films and series together (people too, which callers drop), by popularity. */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = LANGUAGE,
        @Query("include_adult") includeAdult: Boolean = false,
    ): SearchResponse<MultiResult>

    /** Popular titles on given providers ("8|1899") in a country, included in a subscription. */
    @GET("discover/{type}")
    suspend fun discover(
        @Path("type") type: String,
        @Query("with_watch_providers") providers: String?,
        @Query("watch_region") region: String?,
        @Query("with_watch_monetization_types") monetization: String? = "flatrate",
        @Query("with_genres") genres: String? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = LANGUAGE,
        @Query("include_adult") includeAdult: Boolean = false,
    ): SearchResponse<DiscoverResult>

    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: Int,
        @Query("language") language: String = LANGUAGE,
        @Query("append_to_response") append: String = "credits",
    ): MovieDetails

    @GET("tv/{id}")
    suspend fun tv(
        @Path("id") id: Int,
        @Query("language") language: String = LANGUAGE,
        @Query("append_to_response") append: String = "credits",
    ): TvDetails

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("language") language: String = LANGUAGE,
    ): SeasonDetails

    /** Where a title can be watched, per country ("flatrate" = subscription). Data by JustWatch. */
    @GET("{type}/{id}/watch/providers")
    suspend fun watchProviders(
        @Path("type") type: String,
        @Path("id") id: Int,
    ): WatchProvidersResponse

    /** The streaming services TMDB knows in a country, with their order there. */
    @GET("watch/providers/{type}")
    suspend fun regionProviders(
        @Path("type") type: String,
        @Query("watch_region") region: String,
        @Query("language") language: String = LANGUAGE,
    ): SearchResponse<RegionProvider>

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        /** Placeholder the TMDB client replaces with the app's language (see NetworkModule). */
        const val LANGUAGE = "app"
        const val FALLBACK_LANGUAGE = "en-US"
    }
}

@Serializable
data class SearchResponse<T>(val results: List<T> = emptyList())

@Serializable
data class MovieResult(
    val id: Int,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    val popularity: Double = 0.0,
)

@Serializable
data class TvResult(
    val id: Int,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    val popularity: Double = 0.0,
)

@Serializable
data class Genre(val name: String)

@Serializable
data class CastMember(val name: String, val order: Int = 0)

@Serializable
data class CrewMember(val name: String, val job: String? = null)

@Serializable
data class Credits(val cast: List<CastMember> = emptyList(), val crew: List<CrewMember> = emptyList())

@Serializable
data class Creator(val name: String)

@Serializable
data class MovieDetails(
    val id: Int,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
)

@Serializable
data class TvDetails(
    val id: Int,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<Genre> = emptyList(),
    @SerialName("created_by") val createdBy: List<Creator> = emptyList(),
    val credits: Credits? = null,
    val seasons: List<SeasonSummary> = emptyList(),
    /** "Returning Series", "Ended", "Canceled", "In Production", "Planned". */
    val status: String? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: NextEpisode? = null,
)

@Serializable
data class NextEpisode(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
)

@Serializable
data class SeasonDetails(val episodes: List<EpisodeDetails> = emptyList())

@Serializable
data class SeasonSummary(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class EpisodeDetails(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
)

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun poster(path: String?): String? = path?.let { "${BASE}w342$it" }
    fun backdrop(path: String?): String? = path?.let { "${BASE}w1280$it" }
    fun still(path: String?): String? = path?.let { "${BASE}w300$it" }
    fun logo(path: String?): String? = path?.let { "${BASE}w154$it" }
}

@Serializable
data class WatchProvidersResponse(val results: Map<String, CountryProviders> = emptyMap())

@Serializable
data class CountryProviders(
    val link: String? = null,
    val flatrate: List<ProviderEntry> = emptyList(),
    val free: List<ProviderEntry> = emptyList(),
    val ads: List<ProviderEntry> = emptyList(),
    val rent: List<ProviderEntry> = emptyList(),
    val buy: List<ProviderEntry> = emptyList(),
)

@Serializable
data class ProviderEntry(
    @SerialName("provider_id") val id: Int,
    @SerialName("provider_name") val name: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val priority: Int = 0,
)

@Serializable
data class RegionProvider(
    @SerialName("provider_id") val id: Int,
    @SerialName("provider_name") val name: String,
    @SerialName("display_priorities") val priorities: Map<String, Int> = emptyMap(),
    @SerialName("display_priority") val priority: Int = Int.MAX_VALUE,
)

/** A search/multi hit: a film (title, release_date) or a series (name, first_air_date). */
@Serializable
data class MultiResult(
    val id: Int,
    @SerialName("media_type") val mediaType: String,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val popularity: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
)

/** A discover hit: films have title/release_date, series name/first_air_date. */
@Serializable
data class DiscoverResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val popularity: Double = 0.0,
)
