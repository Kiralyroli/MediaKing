package com.kiroland.mediacenter.data.library

import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MetadataDao
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A title as the watched list stores it (from TMDB, whether in the library or not). */
data class WatchedTitle(val isMovie: Boolean, val tmdbId: Int, val title: String, val year: Int?, val posterPath: String?)

/** The user's watched films and series and their ratings: their own record of what they have seen. */
@Singleton
class WatchedRepository @Inject constructor(
    private val dao: MetadataDao,
    private val libraryDao: LibraryDao,
) {
    val all: Flow<List<WatchedTitleEntity>> = dao.observeWatched()

    fun entry(isMovie: Boolean, tmdbId: Int): Flow<WatchedTitleEntity?> = dao.observeWatchedEntry(key(isMovie, tmdbId))

    /** Marks it watched now, keeping an earlier date and rating; it leaves the watchlist. */
    suspend fun markWatched(title: WatchedTitle) {
        val key = key(title.isMovie, title.tmdbId)
        val old = dao.watchedEntry(key)
        dao.upsertWatched(
            WatchedTitleEntity(
                key = key,
                isMovie = title.isMovie,
                tmdbId = title.tmdbId,
                title = title.title,
                year = title.year,
                posterPath = title.posterPath,
                watchedAt = old?.watchedAt ?: System.currentTimeMillis(),
                rating = old?.rating,
            ),
        )
        dao.removeFromWatchlist(key)
    }

    suspend fun unmark(isMovie: Boolean, tmdbId: Int) = dao.deleteWatched(key(isMovie, tmdbId))

    /** [rating] 1..5, or null to clear it. */
    suspend fun rate(isMovie: Boolean, tmdbId: Int, rating: Int?) =
        dao.rateWatched(key(isMovie, tmdbId), rating?.coerceIn(1, 5))

    /** A library film played to the end (or marked watched) goes on the list, if TMDB knows it. */
    suspend fun recordLibraryFilm(path: String) {
        val item = libraryDao.observeMedia(path).first() ?: return
        if (item.media.kind != MediaKind.MOVIE) return
        val meta = item.metadata ?: return
        val tmdbId = meta.tmdbId ?: return
        markWatched(WatchedTitle(true, tmdbId, item.displayTitle, item.displayYear, meta.posterPath))
    }

    companion object {
        /** Same keys as the watchlist, so one can clear the other. */
        fun key(isMovie: Boolean, tmdbId: Int) = (if (isMovie) "movie:" else "tv:") + tmdbId
    }
}
