package com.kiroland.mediacenter.data.streaming

import com.kiroland.mediacenter.data.metadata.tmdb.CountryProviders
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages

/** How a title can be watched at a provider, best first. */
enum class Offer { SUBSCRIPTION, FREE, RENT, BUY }

/** One provider offering a title, as TMDB (JustWatch) lists it for Hungary. */
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
        10 to PRIME,
        1899 to ProviderApp(listOf("com.wbd.stream", "com.hbo.hbonow"), "https://www.hbomax.com/", "https://play.hbomax.com/search"),
        337 to ProviderApp(listOf("com.disney.disneyplus"), "https://www.disneyplus.com/", "https://www.disneyplus.com/search"),
        1773 to ProviderApp(listOf("com.skyshowtime.skyshowtime.google", "com.skyshowtime.skyshowtime"), "https://www.skyshowtime.com/"),
        350 to APPLE,
        2 to APPLE,
        3 to ProviderApp(listOf("com.google.android.videos"), "https://play.google.com/store/movies"),
        188 to ProviderApp(listOf("com.google.android.youtube.tv", "com.google.android.youtube"), "https://www.youtube.com/"),
        35 to ProviderApp(listOf("tv.wuaki.apptv", "tv.wuaki"), "https://www.rakuten.tv/hu"),
        2695 to ProviderApp(listOf("hu.mtva.mediaklikk"), "https://mediaklikk.hu/"),
        11 to ProviderApp(listOf("com.mubi"), "https://mubi.com/"),
        283 to ProviderApp(listOf("com.crunchyroll.crunchyroid"), "https://www.crunchyroll.com/"),
        538 to ProviderApp(listOf("com.plexapp.android"), "https://watch.plex.tv/"),
        223 to ProviderApp(listOf("com.hayu.hayu", "com.nbcuni.hayu"), "https://www.hayu.com/"),
        701 to ProviderApp(listOf("com.spiintl.tv.filmbox", "com.spiintl.filmbox"), "https://www.filmboxplus.com/"),
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
    "https://tv.apple.com/hu",
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
    /** The subscription services offered in Hungary that matter here, in TMDB's Hungarian order. */
    val choices = listOf(
        Subscribable(8, "Netflix"),
        Subscribable(119, "Amazon Prime Video"),
        Subscribable(1899, "HBO Max"),
        Subscribable(337, "Disney+"),
        Subscribable(1773, "SkyShowtime"),
        Subscribable(350, "Apple TV+"),
        Subscribable(188, "YouTube Premium"),
        Subscribable(11, "MUBI"),
        Subscribable(701, "FilmBox+"),
        Subscribable(223, "Hayu"),
        Subscribable(283, "Crunchyroll"),
    )

    /** YouTube comes with every Android TV: having the app says nothing about a Premium subscription. */
    private val notImpliedByApp = setOf(188)

    /** Chosen in the settings, or until then the services whose app is installed. */
    fun effective(chosen: Set<Int>?, installedProviders: Set<Int>): Set<Int> =
        chosen ?: choices.map { it.providerId }.filterTo(HashSet()) { it in installedProviders && it !in notImpliedByApp }

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
