package com.kiroland.mediacenter.data.streaming

import android.content.res.Resources
import android.icu.util.ULocale
import java.text.Collator
import java.util.Locale

/**
 * The country "where to watch" is about (ISO 3166 alpha-2, as TMDB/JustWatch use it): the one chosen
 * in the settings, else the TV's own region, else the US.
 */
object Regions {
    const val HUNGARY = "HU"
    private const val FALLBACK = "US"

    /**
     * The TV's region setting; the system resources, as the app may have replaced the default locale.
     * A locale without a country ("hu") gets its language's likely one ("HU").
     */
    fun system(): String? {
        val locale = Resources.getSystem().configuration.locales.get(0)
        return normalize(locale.country) ?: normalize(ULocale.addLikelySubtags(ULocale.forLocale(locale)).country)
    }

    fun effective(chosen: String?): String = resolve(chosen, system())

    /** [chosen] wins; otherwise the [system] region, if it is a real country code. */
    fun resolve(chosen: String?, system: String?): String = normalize(chosen) ?: normalize(system) ?: FALLBACK

    fun displayName(code: String, locale: Locale): String =
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifBlank { code }

    /** Every country, by name in [locale]. */
    fun all(locale: Locale): List<String> {
        val collator = Collator.getInstance(locale)
        return Locale.getISOCountries().sortedWith { a, b -> collator.compare(displayName(a, locale), displayName(b, locale)) }
    }

    private fun normalize(code: String?): String? =
        code?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
}
