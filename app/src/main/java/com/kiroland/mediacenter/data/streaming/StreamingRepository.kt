package com.kiroland.mediacenter.data.streaming

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.kiroland.mediacenter.data.metadata.TmdbCredentials
import com.kiroland.mediacenter.data.settings.SettingsRepository
import com.kiroland.mediacenter.data.library.db.MetadataDao
import com.kiroland.mediacenter.data.library.db.WatchlistEntity
import com.kiroland.mediacenter.data.metadata.tmdb.DiscoverResult
import kotlinx.coroutines.flow.Flow
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Where to watch a title in Hungary, and opening it in the provider's app. */
@Singleton
class StreamingRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: TmdbApi,
    private val credentials: TmdbCredentials,
    private val settings: SettingsRepository,
    private val dao: MetadataDao,
) {
    data class Availability(val offers: List<ProviderOffer>)

    /** A film or series found on TMDB, whether or not it is in the library. */
    data class Title(val isMovie: Boolean, val tmdbId: Int, val title: String, val year: Int?, val posterPath: String?)

    /** Films and series matching [query] on TMDB; empty without a token or when offline. */
    suspend fun search(query: String): List<Title> {
        if (!credentials.isConfigured || query.isBlank()) return emptyList()
        return try {
            api.searchMulti(query).results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                // TMDB's adult flag misses plenty; obscure entries with no poster and hardly any votes are
                // also rarely on a streaming service, so only reasonably known titles are listed.
                .filter { it.posterPath != null && (it.voteCount >= MIN_VOTES || it.popularity >= MIN_POPULARITY) }
                .map { r ->
                    val movie = r.mediaType == "movie"
                    Title(
                        isMovie = movie,
                        tmdbId = r.id,
                        title = (if (movie) r.title ?: r.originalTitle else r.name ?: r.originalName).orEmpty(),
                        year = (if (movie) r.releaseDate else r.firstAirDate)?.take(4)?.toIntOrNull(),
                        posterPath = r.posterPath,
                    )
                }
                .filter { it.title.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Search for '$query' failed: ${e.message}")
            emptyList()
        }
    }

    /** Offers change often, so they are kept for a few hours only, in memory. */
    private val cache = HashMap<String, Pair<Long, Availability>>()

    /** null if unknown (no token, offline); an empty list if no provider in Hungary has it. */
    suspend fun availability(isMovie: Boolean, tmdbId: Int): Availability? {
        if (!credentials.isConfigured) return null
        val key = (if (isMovie) "movie:" else "tv:") + tmdbId
        synchronized(cache) {
            cache[key]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        }
        return try {
            val country = api.watchProviders(if (isMovie) "movie" else "tv", tmdbId).results[REGION]
            val result = Availability(country?.let(StreamingProviders::merge).orEmpty())
            synchronized(cache) { cache[key] = System.currentTimeMillis() to result }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Watch providers for $key failed: ${e.message}")
            null
        }
    }

    /** Provider apps installed right now (they can be installed while the app runs). */
    fun installedPackages(): Set<String> = StreamingProviders.allPackages.filterTo(HashSet()) { isInstalled(it) }

    private val popularCache = HashMap<Int, Pair<Long, List<Title>>>()

    /** What is popular on one provider in Hungary (films and series together); empty when unknown. */
    suspend fun popularOn(providerId: Int): List<Title> {
        if (!credentials.isConfigured) return emptyList()
        synchronized(popularCache) {
            popularCache[providerId]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        }
        return try {
            fun List<DiscoverResult>.titles(isMovie: Boolean) = filter { it.posterPath != null }.map {
                Title(isMovie, it.id, (if (isMovie) it.title else it.name).orEmpty(), (if (isMovie) it.releaseDate else it.firstAirDate)?.take(4)?.toIntOrNull(), it.posterPath) to it.popularity
            }
            val movies = api.discover("movie", providerId.toString()).results.titles(isMovie = true)
            val series = api.discover("tv", providerId.toString()).results.titles(isMovie = false)
            val result = mergeByPopularity(movies, series, { it.second }, { it.first.title }, limit = 20).map { it.first }
            synchronized(popularCache) { popularCache[providerId] = System.currentTimeMillis() to result }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Popular on $providerId failed: ${e.message}")
            emptyList()
        }
    }

    private val genreCache = HashMap<String, Pair<Long, List<Title>>>()

    /**
     * Popular titles of a genre on the user's services (or anywhere, if none are set), films and
     * series together; empty when unknown.
     */
    suspend fun byGenre(genre: BrowseGenre): List<Title> {
        if (!credentials.isConfigured) return emptyList()
        val providers = mySubscriptions().sorted().joinToString("|").ifEmpty { null }
        val cacheKey = genre.name + "/" + providers
        synchronized(genreCache) {
            genreCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        }
        return try {
            suspend fun fetch(type: String, genres: String?, isMovie: Boolean): List<Pair<Title, Double>> {
                genres ?: return emptyList()
                return api.discover(
                    type = type,
                    providers = providers,
                    region = if (providers == null) null else REGION,
                    monetization = if (providers == null) null else "flatrate",
                    genres = genres,
                    // Without a provider filter the list would start with barely known titles.
                    minVotes = if (providers == null) MIN_VOTES else null,
                ).results.filter { it.posterPath != null }.map {
                    Title(isMovie, it.id, (if (isMovie) it.title else it.name).orEmpty(), (if (isMovie) it.releaseDate else it.firstAirDate)?.take(4)?.toIntOrNull(), it.posterPath) to it.popularity
                }
            }
            val result = mergeByPopularity(
                fetch("movie", genre.movieGenres, isMovie = true),
                fetch("tv", genre.tvGenres, isMovie = false),
                { it.second }, { it.first.title }, limit = 30,
            ).map { it.first }
            synchronized(genreCache) { genreCache[cacheKey] = System.currentTimeMillis() to result }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Genre ${genre.name} failed: ${e.message}")
            emptyList()
        }
    }

    // --- Watchlist ---

    val watchlist: Flow<List<WatchlistEntity>> = dao.observeWatchlist()

    fun isInWatchlist(isMovie: Boolean, tmdbId: Int): Flow<Boolean> = dao.observeInWatchlist(watchlistKey(isMovie, tmdbId))

    suspend fun setInWatchlist(title: Title, inList: Boolean) {
        val key = watchlistKey(title.isMovie, title.tmdbId)
        if (inList) {
            dao.addToWatchlist(WatchlistEntity(key, title.isMovie, title.tmdbId, title.title, title.year, title.posterPath, System.currentTimeMillis()))
        } else {
            dao.removeFromWatchlist(key)
        }
    }

    private fun watchlistKey(isMovie: Boolean, tmdbId: Int) = (if (isMovie) "movie:" else "tv:") + tmdbId

    /** The user's subscriptions: chosen in the settings, or the services whose app is installed. */
    fun mySubscriptions(): Set<Int> = Subscriptions.effective(
        settings.current.subscriptions,
        Subscriptions.choices.map { it.providerId }.filterTo(HashSet(), ::isAppInstalled),
    )

    fun isAppInstalled(providerId: Int): Boolean =
        StreamingProviders.apps[providerId]?.packages?.any { isInstalled(it) } == true

    /** What opening a provider did, so the screen can tell what to do next. */
    enum class Opened { TITLE_SEARCHED, SEARCH_PAGE, APP, STORE, WEBSITE, FAILED }

    fun open(providerId: Int, title: String): Opened {
        val app = StreamingProviders.apps[providerId] ?: return Opened.FAILED
        val step = StreamingProviders.plan(app, installedPackages(), title).firstOrNull(::tryStep) ?: return Opened.FAILED
        return when (step) {
            is StreamingProviders.OpenStep.LinkInApp -> if (app.searchFillsTitle) Opened.TITLE_SEARCHED else Opened.SEARCH_PAGE
            is StreamingProviders.OpenStep.LaunchApp -> Opened.APP
            is StreamingProviders.OpenStep.StorePage -> Opened.STORE
            is StreamingProviders.OpenStep.Website -> Opened.WEBSITE
        }
    }

    private fun tryStep(step: StreamingProviders.OpenStep): Boolean {
        val intent = when (step) {
            is StreamingProviders.OpenStep.LinkInApp ->
                Intent(Intent.ACTION_VIEW, Uri.parse(step.url)).setPackage(step.packageName)
            is StreamingProviders.OpenStep.LaunchApp -> launchIntent(step.packageName) ?: return false
            is StreamingProviders.OpenStep.StorePage ->
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${step.packageName}")).setPackage(PLAY_STORE)
            is StreamingProviders.OpenStep.Website ->
                Intent(Intent.ACTION_VIEW, Uri.parse(step.url)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private val packages: PackageManager get() = context.packageManager

    /** TV apps register a leanback launcher entry; phone-first apps only a normal one. */
    private fun launchIntent(packageName: String): Intent? =
        packages.getLeanbackLaunchIntentForPackage(packageName) ?: packages.getLaunchIntentForPackage(packageName)

    private fun isInstalled(packageName: String): Boolean =
        runCatching { packages.getPackageInfo(packageName, 0) }.isSuccess

    private companion object {
        const val TAG = "StreamingRepository"
        const val REGION = "HU"
        const val PLAY_STORE = "com.android.vending"
        const val CACHE_MS = 6L * 60 * 60 * 1000
        const val MIN_VOTES = 20
        const val MIN_POPULARITY = 3.0
    }
}
