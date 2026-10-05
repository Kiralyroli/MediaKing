package com.kiroland.mediacenter.data.library.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LookupInput(val title: String, val year: Int?, val kind: MediaKind)

data class SeasonToFetch(val tvId: Int, val season: Int)

@Dao
interface MetadataDao {

    /** Keys used by library files with no metadata yet, or whose "not found" is old enough to retry. */
    @Query(
        """SELECT DISTINCT m.metadataKey FROM media m
           WHERE m.metadataKey IS NOT NULL AND m.metadataKey NOT IN (
               SELECT key FROM metadata WHERE tmdbId IS NOT NULL OR fetchedAt > :retryNotFoundAfter
           )""",
    )
    suspend fun keysToFetch(retryNotFoundAfter: Long): List<String>

    @Query("SELECT title, year, kind FROM media WHERE metadataKey = :key")
    suspend fun lookupInputs(key: String): List<LookupInput>

    @Upsert
    suspend fun upsert(metadata: MetadataEntity)

    @Query(
        """SELECT DISTINCT md.tmdbId AS tvId, m.season AS season FROM media m
           JOIN metadata md ON md.key = m.metadataKey
           WHERE m.kind = 'EPISODE' AND md.tmdbId IS NOT NULL AND m.season IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM episode_metadata e WHERE e.tvId = md.tmdbId AND e.season = m.season)""",
    )
    suspend fun seasonsToFetch(): List<SeasonToFetch>

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeMetadataEntity>)

    @Query("SELECT * FROM episode_metadata WHERE tvId = :tvId ORDER BY season, episode")
    fun observeEpisodes(tvId: Int): Flow<List<EpisodeMetadataEntity>>

    // --- Seasons, including those not in the library ---

    /** Matched shows whose season list is missing or older than [before]. */
    @Query(
        """SELECT DISTINCT md.tmdbId FROM metadata md
           WHERE md.key LIKE 'tv:%' AND md.tmdbId IS NOT NULL
             AND md.key IN (SELECT metadataKey FROM media WHERE metadataKey IS NOT NULL)
             AND NOT EXISTS (SELECT 1 FROM season_metadata s WHERE s.tvId = md.tmdbId AND s.fetchedAt > :before)""",
    )
    suspend fun showsToRefresh(before: Long): List<Int>

    @Query("SELECT * FROM season_metadata WHERE tvId = :tvId")
    suspend fun seasons(tvId: Int): List<SeasonMetadataEntity>

    @Query("SELECT * FROM season_metadata WHERE tvId = :tvId ORDER BY season")
    fun observeSeasons(tvId: Int): Flow<List<SeasonMetadataEntity>>

    @Upsert
    suspend fun upsertSeasons(seasons: List<SeasonMetadataEntity>)

    @Query("DELETE FROM season_metadata WHERE tvId = :tvId AND season NOT IN (:keep)")
    suspend fun deleteSeasonsExcept(tvId: Int, keep: List<Int>)

    /** Listed seasons whose episodes are not loaded yet (specials only once they are in the library). */
    @Query(
        """SELECT s.tvId AS tvId, s.season AS season FROM season_metadata s
           WHERE s.episodesFetchedAt IS NULL AND s.episodeCount > 0 AND s.season > 0""",
    )
    suspend fun listedSeasonsToFetch(): List<SeasonToFetch>

    @Query("UPDATE season_metadata SET episodesFetchedAt = :at WHERE tvId = :tvId AND season = :season")
    suspend fun markEpisodesFetched(tvId: Int, season: Int, at: Long)

    @Query("DELETE FROM season_metadata")
    suspend fun clearSeasons()

    // --- Skippable segments from TheIntroDB ---

    @Query("SELECT * FROM segment_cache WHERE `key` = :key")
    suspend fun cachedSegments(key: String): SegmentCacheEntity?

    @Upsert
    suspend fun cacheSegments(entry: SegmentCacheEntity)

    // --- Watchlist ---

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE `key` = :key)")
    fun observeInWatchlist(key: String): Flow<Boolean>

    @Upsert
    suspend fun addToWatchlist(entry: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE `key` = :key")
    suspend fun removeFromWatchlist(key: String)

    @Query("DELETE FROM metadata")
    suspend fun clearMetadata()

    @Query("DELETE FROM episode_metadata")
    suspend fun clearEpisodes()

    @Query("SELECT * FROM metadata WHERE key = :key")
    fun observe(key: String): Flow<MetadataEntity?>
}
