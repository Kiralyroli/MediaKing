package com.kiroland.mediacenter.data.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM title_watch WHERE `key` = :key")
    suspend fun watched(key: String): TitleWatchEntity?

    @Upsert
    suspend fun upsertWatch(entry: TitleWatchEntity)

    /** Ignores events already recorded, dismissed ones included. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNews(news: List<TitleNewsEntity>)

    @Query("SELECT * FROM title_news WHERE dismissed = 0 AND createdAt >= :since ORDER BY createdAt DESC")
    fun observeNews(since: Long): Flow<List<TitleNewsEntity>>

    @Query("UPDATE title_news SET dismissed = 1 WHERE isMovie = :isMovie AND tmdbId = :tmdbId")
    suspend fun dismiss(isMovie: Boolean, tmdbId: Int)

    @Query("DELETE FROM title_news WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
