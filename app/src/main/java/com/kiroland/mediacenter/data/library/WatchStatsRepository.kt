package com.kiroland.mediacenter.data.library

import android.util.Log
import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.metadata.TmdbCredentials
import com.kiroland.mediacenter.util.AppLocale
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** The watched list's statistics; genres and runtimes come from TMDB, kept in memory. */
@Singleton
class WatchStatsRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val metadata: MetadataRepository,
    private val credentials: TmdbCredentials,
) {
    private val cache = HashMap<String, TitleDetails>()

    suspend fun stats(titles: List<WatchedTitleEntity>): WatchStats {
        val details = titles.mapNotNull { title -> details(title)?.let { title.key to it } }.toMap()
        return WatchStats.compute(titles, details, libraryDao.finishedEpisodes(), System.currentTimeMillis())
    }

    private suspend fun details(title: WatchedTitleEntity): TitleDetails? {
        // Genre names are in the app's language, so a language change asks again.
        val cacheKey = title.key + "/" + AppLocale.tmdbLanguage
        synchronized(cache) { cache[cacheKey] }?.let { return it }
        if (!credentials.isConfigured) return null
        return try {
            val entity = metadata.preview(title.isMovie, title.tmdbId)
            TitleDetails(
                genres = entity.genres?.split(", ")?.filter { it.isNotBlank() }.orEmpty(),
                runtimeMinutes = entity.runtimeMinutes,
            ).also { synchronized(cache) { cache[cacheKey] = it } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WatchStats", "Details for ${title.key} failed: ${e.message}")
            null
        }
    }
}
