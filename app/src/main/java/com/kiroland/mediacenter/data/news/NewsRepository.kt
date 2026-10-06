package com.kiroland.mediacenter.data.news

import android.content.Context
import android.util.Log
import com.kiroland.mediacenter.data.library.SeasonState
import com.kiroland.mediacenter.data.library.WatchedRepository
import com.kiroland.mediacenter.data.library.db.MetadataDao
import com.kiroland.mediacenter.data.library.db.NewsDao
import com.kiroland.mediacenter.data.library.db.TitleNewsEntity
import com.kiroland.mediacenter.data.library.db.TitleWatchEntity
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.metadata.TmdbCredentials
import com.kiroland.mediacenter.data.streaming.Offer
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * News about followed titles: the watchlist (services and seasons), and series in the library or
 * marked watched (seasons). Checked every few hours while the app runs; shown on the home screen.
 */
@Singleton
class NewsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: NewsDao,
    private val metadataDao: MetadataDao,
    private val metadata: MetadataRepository,
    private val streaming: StreamingRepository,
    private val credentials: TmdbCredentials,
) {
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** Unopened news of the last two weeks, newest first. */
    fun news(): Flow<List<TitleNewsEntity>> = dao.observeNews(System.currentTimeMillis() - KEEP_MS)

    /** Opening a title answers its news. */
    suspend fun dismiss(isMovie: Boolean, tmdbId: Int) = dao.dismiss(isMovie, tmdbId)

    suspend fun checkIfDue() {
        if (!credentials.isConfigured) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_CHECKED_AT, 0) < INTERVAL_MS) return
        check()
    }

    private data class Followed(
        val isMovie: Boolean,
        val tmdbId: Int,
        val title: String,
        val posterPath: String?,
        /** Services are asked for watchlist titles only: what is in the library or seen is not wanted there. */
        val askProviders: Boolean,
    )

    suspend fun check() = mutex.withLock {
        val followed = LinkedHashMap<String, Followed>()
        metadataDao.observeWatchlist().first().forEach {
            followed[it.key] = Followed(it.isMovie, it.tmdbId, it.title, it.posterPath, askProviders = true)
        }
        val series = metadataDao.matched().filter { it.key.startsWith("tv:") } // library series
            .mapNotNull { m -> m.tmdbId?.let { Followed(false, it, m.title ?: m.originalTitle.orEmpty(), m.posterPath, false) } } +
            metadataDao.observeWatched().first().filter { !it.isMovie }
                .map { Followed(false, it.tmdbId, it.title, it.posterPath, false) }
        series.forEach { followed.putIfAbsent(WatchedRepository.key(false, it.tmdbId), it) }

        val mine = streaming.mySubscriptions()
        val now = System.currentTimeMillis()
        var found = 0
        for ((key, title) in followed) {
            try {
                val state = stateOf(title, mine) ?: continue
                val previous = dao.watched(key)?.toState(title.askProviders)
                val events = NewsDetector.detect(previous, state.first)
                if (events.isNotEmpty()) {
                    dao.addNews(events.map { it.toEntity(key, title, state.second, now) })
                    found += events.size
                }
                dao.upsertWatch(state.first.toEntity(key, now))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "News check for $key failed: ${e.message}")
            }
        }
        dao.deleteOlderThan(now - KEEP_MS)
        prefs.edit().putLong(KEY_CHECKED_AT, now).apply()
        Log.i(TAG, "Checked ${followed.size} titles, $found news")
    }

    /** The title's state now, with provider names by id; null when TMDB could not be asked. */
    private suspend fun stateOf(title: Followed, mine: Set<Int>): Pair<TitleState, Map<Int, String>>? {
        val names = HashMap<Int, String>()
        val providers = if (title.askProviders) {
            val offers = streaming.availability(title.isMovie, title.tmdbId)?.offers ?: return null
            offers.filter { it.providerId in mine && (Offer.SUBSCRIPTION in it.offers || Offer.FREE in it.offers) }
                .onEach { names[it.providerId] = it.name }
                .mapTo(HashSet()) { it.providerId }
        } else {
            null
        }
        if (title.isMovie) return TitleState(providers, null, null, null) to names
        val facts = metadata.seriesFacts(title.tmdbId)
        val announced = facts.seasons.firstOrNull { it.second == SeasonState.ANNOUNCED }?.first
        return TitleState(providers, facts.airedSeasons, announced?.number, announced?.airDate) to names
    }

    private fun TitleWatchEntity.toState(askProviders: Boolean) = TitleState(
        // Not asked before (it was only in the library): today's services are the baseline, not news.
        providers = if (askProviders && providers != NOT_ASKED) providers.split(',').filter { it.isNotEmpty() }.map { it.toInt() }.toSet() else null,
        airedSeasons = airedSeasons,
        announcedSeason = announcedSeason,
        announcedDate = announcedDate,
    )

    private fun TitleState.toEntity(key: String, now: Long) = TitleWatchEntity(
        key = key,
        providers = providers?.sorted()?.joinToString(",") ?: NOT_ASKED,
        airedSeasons = airedSeasons,
        announcedSeason = announcedSeason,
        announcedDate = announcedDate,
        checkedAt = now,
    )

    private fun NewsEvent.toEntity(key: String, title: Followed, names: Map<Int, String>, now: Long) = TitleNewsEntity(
        id = listOfNotNull(kind.name.lowercase(), key, providerId, season, date).joinToString(":"),
        isMovie = title.isMovie,
        tmdbId = title.tmdbId,
        title = title.title,
        posterPath = title.posterPath,
        kind = kind.name,
        providerName = providerId?.let { names[it] },
        season = season,
        date = date,
        createdAt = now,
    )

    companion object {
        private const val TAG = "NewsRepository"
        private const val PREFS = "news"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val NOT_ASKED = "-"
        const val INTERVAL_MS = 6L * 60 * 60 * 1000
        private const val KEEP_MS = 14L * 24 * 60 * 60 * 1000
    }
}
