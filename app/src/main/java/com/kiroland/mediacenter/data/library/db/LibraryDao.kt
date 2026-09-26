package com.kiroland.mediacenter.data.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val WITH_PROGRESS = """
    SELECT m.*, p.positionMs AS positionMs, p.durationMs AS durationMs, p.finished AS finished,
           p.updatedAt AS progressUpdatedAt
    FROM media m LEFT JOIN watch_progress p ON p.path = m.path
"""

@Dao
interface LibraryDao {

    // --- Library folders ---

    @Query("SELECT * FROM library_folder ORDER BY path")
    fun observeFolders(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folder")
    suspend fun folders(): List<LibraryFolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: LibraryFolderEntity)

    @Transaction
    suspend fun removeFolder(path: String) {
        deleteFolder(path)
        deleteMediaInRoot(path)
    }

    @Query("DELETE FROM library_folder WHERE path = :path")
    suspend fun deleteFolder(path: String)

    @Query("DELETE FROM media WHERE libraryRoot = :root")
    suspend fun deleteMediaInRoot(root: String)

    // --- Scanner ---

    @Query("SELECT path, addedAt FROM media WHERE libraryRoot = :root")
    suspend fun pathsInRoot(root: String): List<PathAddedAt>

    @Upsert
    suspend fun upsertMedia(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE path IN (:paths)")
    suspend fun deleteMedia(paths: List<String>)

    // --- Browsing ---

    @Transaction
    @Query("$WITH_PROGRESS WHERE m.kind = 'MOVIE' ORDER BY m.title COLLATE NOCASE, m.year")
    fun observeMovies(): Flow<List<MediaWithProgress>>

    @Transaction
    @Query("$WITH_PROGRESS WHERE m.kind = 'MOVIE' ORDER BY m.addedAt DESC LIMIT :limit")
    fun observeRecentMovies(limit: Int): Flow<List<MediaWithProgress>>

    @Transaction
    @Query("$WITH_PROGRESS WHERE m.kind = 'EPISODE' ORDER BY m.title COLLATE NOCASE, m.season, m.episode")
    fun observeEpisodes(): Flow<List<MediaWithProgress>>

    @Transaction
    @Query("$WITH_PROGRESS WHERE m.seriesKey = :seriesKey ORDER BY m.season, m.episode")
    fun observeSeries(seriesKey: String): Flow<List<MediaWithProgress>>

    @Transaction
    @Query(
        """$WITH_PROGRESS WHERE p.finished = 0 AND p.positionMs > 0
           ORDER BY p.updatedAt DESC LIMIT :limit""",
    )
    fun observeInProgress(limit: Int): Flow<List<MediaWithProgress>>

    @Transaction
    @Query("$WITH_PROGRESS WHERE m.path = :path")
    fun observeMedia(path: String): Flow<MediaWithProgress?>

    @Query("SELECT * FROM media WHERE path = :path")
    suspend fun media(path: String): MediaEntity?

    @Query(
        """SELECT * FROM media WHERE seriesKey = :seriesKey
           AND (season > :season OR (season = :season AND episode > :episode))
           ORDER BY season, episode LIMIT 1""",
    )
    suspend fun nextEpisode(seriesKey: String, season: Int, episode: Int): MediaEntity?

    // --- Progress ---

    @Query("SELECT * FROM watch_progress WHERE path = :path")
    suspend fun progress(path: String): WatchProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE path = :path")
    suspend fun deleteProgress(path: String)
}
