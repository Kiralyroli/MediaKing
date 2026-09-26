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

    @Query("SELECT * FROM metadata WHERE key = :key")
    fun observe(key: String): Flow<MetadataEntity?>
}
