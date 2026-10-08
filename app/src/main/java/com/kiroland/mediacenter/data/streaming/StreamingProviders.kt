package com.kiroland.mediacenter.data.streaming

import com.kiroland.mediacenter.data.metadata.tmdb.RegionProvider
import com.kiroland.mediacenter.R
import androidx.annotation.StringRes
import com.kiroland.mediacenter.data.metadata.tmdb.CountryProviders
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages

/** How a title can be watched at a provider, best first. */
enum class Offer { SUBSCRIPTION, FREE, RENT, BUY }

/** One provider offering a title, as TMDB (JustWatch) lists it for the user's country. */
data class ProviderOffer(
    val providerId: Int,
    val name: String,
    val logoUrl: String?,
    val offers: Set<Offer>,
    val priority: Int,
) {
    val best: Offer get() = offers.minBy { it.ordinal }
}

/**
 * What we know about a provider's app: its TV and phone packages (first installed one is used),
 * its website, and a link to its search page. Only some apps take the title from that link ({q}):
 * tested on the TV, Prime Video does; HBO Max and Disney+ open an empty search; Netflix has none.
 * TMDB gives no per-title ids of the providers, so a link to the title itself is not possible.
 */
data class ProviderApp(
    val packages: List<String>,
    val website: String,
    val searchUrl: String? = null,
) {
    /** The search link fills in the title, so no typing is needed. */
    val searchFillsTitle: Boolean get() = searchUrl?.contains("{q}") == true
}

object StreamingProviders {

    /** By TMDB provider id. Several ids can share an app (Amazon Video and Prime Video). */
    val apps: Map<Int, ProviderApp> = mapOf(
        8 to ProviderApp(listOf("com.netflix.ninja", "com.netflix.mediaclient"), "https://www.netflix.com/"),
        119 to PRIME,
        9 to PRIME, // Prime Video's id in the US
        10 to PRIME,
        1899 to ProviderApp(listOf("com.wbd.stream", "com.hbo.hbonow"), "https://www.hbomax.com/", "https://play.hbomax.com/search"),
        337 to ProviderApp(listOf("com.disney.disneyplus"), "https://www.disneyplus.com/", "https://www.disneyplus.com/search"),
        1773 to ProviderApp(listOf("com.skyshowtime.skyshowtime.google", "com.skyshowtime.skyshowtime"), "https://www.skyshowtime.com/"),
        350 to APPLE,
        2 to APPLE,
        3 to ProviderApp(listOf("com.google.android.videos"), "https://play.google.com/store/movies"),
        188 to ProviderApp(listOf("com.google.android.youtube.tv", "com.google.android.youtube"), "https://www.youtube.com/"),
        35 to ProviderApp(listOf("tv.wuaki.apptv", "tv.wuaki"), "https://www.rakuten.tv/"),
        2695 to ProviderApp(listOf("hu.mtva.mediaklikk"), "https://mediaklikk.hu/"),
        11 to ProviderApp(listOf("com.mubi"), "https://mubi.com/"),
        283 to ProviderApp(listOf("com.crunchyroll.crunchyroid"), "https://www.crunchyroll.com/"),
        538 to ProviderApp(listOf("com.plexapp.android"), "https://watch.plex.tv/"),
        223 to ProviderApp(listOf("com.hayu.hayu", "com.nbcuni.hayu"), "https://www.hayu.com/"),
        701 to ProviderApp(listOf("com.spiintl.tv.filmbox", "com.spiintl.filmbox"), "https://www.filmboxplus.com/"),
        15 to ProviderApp(listOf("com.hulu.livingroomplus", "com.hulu.plus"), "https://www.hulu.com/"),
        386 to ProviderApp(listOf("com.peacocktv.peacockandroid"), "https://www.peacocktv.com/"),
        531 to ProviderApp(listOf("com.cbs.ott", "com.cbs.app"), "https://www.paramountplus.com/"),
    )

    /** Every package we might launch, for the manifest's <queries> and the installed check. */
    val allPackages: Set<String> get() = apps.values.flatMap { it.packages }.toSet()

    /** Merges TMDB's per-offer lists into one entry per provider, best offer first, then TMDB's order. */
    fun merge(country: CountryProviders): List<ProviderOffer> {
        val byOffer = listOf(
            Offer.SUBSCRIPTION to country.flatrate,
            Offer.FREE to country.free + country.ads,
            Offer.RENT to country.rent,
            Offer.BUY to country.buy,
        )
        val merged = LinkedHashMap<Int, ProviderOffer>()
        for ((offer, entries) in byOffer) {
            for (entry in entries) {
                val old = merged[entry.id]
                merged[entry.id] = old?.copy(offers = old.offers + offer)
                    ?: ProviderOffer(entry.id, entry.name, TmdbImages.logo(entry.logoPath), setOf(offer), entry.priority)
            }
        }
        return merged.values.sortedWith(compareBy<ProviderOffer> { it.best.ordinal }.thenBy { it.priority })
    }

    /**
     * Ways to open [app], tried in order: its search for the title (when known), the app itself,
     * the Play Store page of its TV package, the website.
     */
    fun plan(app: ProviderApp, installed: Set<String>, title: String): List<OpenStep> {
        val pkg = app.packages.firstOrNull { it in installed }
        if (pkg == null) {
            return listOf(OpenStep.StorePage(app.packages.first()), OpenStep.Website(app.website))
        }
        return buildList {
            app.searchUrl?.let { add(OpenStep.LinkInApp(pkg, it.replace("{q}", java.net.URLEncoder.encode(title, "UTF-8")))) }
            add(OpenStep.LaunchApp(pkg))
        }
    }

    sealed interface OpenStep {
        data class LinkInApp(val packageName: String, val url: String) : OpenStep
        data class LaunchApp(val packageName: String) : OpenStep
        data class StorePage(val packageName: String) : OpenStep
        data class Website(val url: String) : OpenStep
    }
}

private val PRIME = ProviderApp(
    listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
    "https://www.primevideo.com/",
    "https://app.primevideo.com/search?phrase={q}",
)

private val APPLE = ProviderApp(
    listOf("com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv"),
    "https://tv.apple.com/",
)

/**
 * The title to search for in a provider's app: the original one, which their search finds more
 * reliably than a Hungarian translation, unless it is not in Latin script (no way to type it on a TV).
 */
fun searchTitle(originalTitle: String?, localTitle: String): String =
    originalTitle?.takeIf { it.isNotBlank() && isLatin(it) } ?: localTitle

/** Only Latin letters (accents are fine): what a Hungarian viewer can read and type. */
fun isLatin(text: String): Boolean = text.all { c -> !c.isLetter() || Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN }

/** Films and series from two popularity-sorted lists, merged by popularity, readable titles only. */
fun <T> mergeByPopularity(movies: List<T>, series: List<T>, popularity: (T) -> Double, title: (T) -> String, limit: Int): List<T> =
    (movies + series).filter { isLatin(title(it)) }.sortedByDescending(popularity).take(limit)

/** A subscription service the user can tick in the settings. */
data class Subscribable(val providerId: Int, val name: String)

object Subscriptions {
    /** Offered when the country's own list cannot be fetched (offline, no token): the big international ones. */
    val fallback = listOf(
        Subscribable(8, "Netflix"),
        Subscribable(119, "Amazon Prime Video"),
        Subscribable(337, "Disney+"),
        Subscribable(1899, "HBO Max"),
        Subscribable(350, "Apple TV+"),
        Subscribable(188, "YouTube Premium"),
        Subscribable(283, "Crunchyroll"),
        Subscribable(11, "MUBI"),
    )

    /**
     * YouTube comes with every Android TV: having the app says nothing about a Premium subscription.
     * Neither does a store app that only rents and sells.
     */
    private val notImpliedByApp = setOf(188, 2, 3, 10) // and the stores: Apple TV, Google Play, Amazon Video

    /**
     * Not subscriptions of their own: ad tiers, channels sold inside another service, stores that only
     * rent and sell, and guides.
     */
    private val NOT_A_SERVICE = Regex("""\bwith ads\b|\bchannel\b|\bstandard with\b|\bstore\b|justwatch""", RegexOption.IGNORE_CASE)

    /** Stores by id (Apple TV Store, Google Play, Fandango at Home, Amazon Video, Microsoft, YouTube, Rakuten TV). */
    private val STORES = setOf(2, 3, 7, 10, 68, 192, 35)

    /**
     * Every service in a country, from TMDB's film and series provider lists: in that country's order,
     * a service on both lists once.
     */
    fun servicesIn(region: String, movie: List<RegionProvider>, tv: List<RegionProvider>): List<Subscribable> =
        (movie + tv)
            .filterNot { it.id in STORES || NOT_A_SERVICE.containsMatchIn(it.name) }
            .groupBy { it.id }
            .map { (id, entries) -> Triple(id, entries.first().name, entries.minOf { it.priorities[region] ?: it.priority }) }
            .sortedBy { it.third }
            .map { (id, name, _) -> Subscribable(id, name) }

    /** What to offer: the country's [limit] biggest, plus any further down that the user already has ([keep]). */
    fun choices(services: List<Subscribable>, keep: Set<Int>, limit: Int = 20): List<Subscribable> =
        services.take(limit) + services.drop(limit).filter { it.providerId in keep }

    /** Chosen in the settings, or until then the services whose app is installed. */
    fun effective(chosen: Set<Int>?, installedProviders: Set<Int>): Set<Int> =
        chosen ?: installedProviders.filterTo(HashSet()) { it !in notImpliedByApp }

    /**
     * Offers sorted for this user: what their subscriptions include first, then other subscriptions,
     * free, rent and buy (each in TMDB's order).
     */
    fun sortForUser(offers: List<ProviderOffer>, mine: Set<Int>): List<ProviderOffer> =
        offers.sortedWith(compareBy<ProviderOffer> { !isIncluded(it, mine) }.thenBy { it.best.ordinal }.thenBy { it.priority })

    /**
     * The user can watch it at no extra cost: in one of their subscriptions, or free outright. A
     * subscription service that also has a free tier (Apple TV gives some episodes away) still needs
     * the subscription.
     */
    fun isIncluded(offer: ProviderOffer, mine: Set<Int>): Boolean =
        if (Offer.SUBSCRIPTION in offer.offers) offer.providerId in mine else Offer.FREE in offer.offers
}

/**
 * A genre to browse by. TMDB numbers film and series genres differently; null when the other kind
 * has no such genre (no "Horror" for series).
 */
data class BrowseGenre(@StringRes val name: Int, val movieGenres: String?, val tvGenres: String?)

object Genres {
    val all = listOf(
        BrowseGenre(R.string.genre_action, "28|12", "10759"),
        BrowseGenre(R.string.genre_comedy, "35", "35"),
        BrowseGenre(R.string.genre_drama, "18", "18"),
        BrowseGenre(R.string.genre_crime, "80", "80"),
        BrowseGenre(R.string.genre_thriller, "53", null),
        BrowseGenre(R.string.genre_scifi, "878|14", "10765"),
        BrowseGenre(R.string.genre_horror, "27", null),
        BrowseGenre(R.string.genre_mystery, "9648", "9648"),
        BrowseGenre(R.string.genre_romance, "10749", null),
        BrowseGenre(R.string.genre_animation, "16", "16"),
        BrowseGenre(R.string.genre_family, "10751", "10751"),
        BrowseGenre(R.string.genre_documentary, "99", "99"),
        BrowseGenre(R.string.genre_war, "10752", "10768"),
    )
}
