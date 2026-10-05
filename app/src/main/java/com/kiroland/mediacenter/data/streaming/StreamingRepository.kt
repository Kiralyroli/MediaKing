package com.kiroland.mediacenter.data.streaming

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.kiroland.mediacenter.data.metadata.TmdbCredentials
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
) {
    data class Availability(val offers: List<ProviderOffer>)

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
    }
}
