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

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val LANGUAGE = "hu-HU"
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
    val popularity: Double = 0.0,
)

@Serializable
data class TvResult(
    val id: Int,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
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
)

@Serializable
data class SeasonDetails(val episodes: List<EpisodeDetails> = emptyList())

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
}
