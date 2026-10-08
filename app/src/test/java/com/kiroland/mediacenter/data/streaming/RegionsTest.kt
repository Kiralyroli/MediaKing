package com.kiroland.mediacenter.data.streaming

import com.kiroland.mediacenter.data.metadata.tmdb.RegionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RegionsTest {

    @Test
    fun `the chosen country wins, then the TV's, then the US`() {
        assertEquals("AT", Regions.resolve("AT", "HU"))
        assertEquals("HU", Regions.resolve(null, "HU"))
        assertEquals("DE", Regions.resolve(null, "de"))
        assertEquals("US", Regions.resolve(null, null))
        // Not countries: an empty region, a numeric UN region ("419" = Latin America).
        assertEquals("US", Regions.resolve(null, ""))
        assertEquals("US", Regions.resolve(null, "419"))
    }

    @Test
    fun `countries are listed by name in the app's language`() {
        val hungarian = Regions.all(Locale.forLanguageTag("hu"))
        assertTrue(hungarian.size > 200)
        assertEquals("Magyarország", Regions.displayName("HU", Locale.forLanguageTag("hu")))
        assertEquals("Hungary", Regions.displayName("HU", Locale.ENGLISH))
        // Hungarian order: "Ausztria" before "Ázsia"-like accented names is the collator's job; check one pair.
        assertTrue(hungarian.indexOf("AT") < hungarian.indexOf("BE"))
    }

    private fun provider(id: Int, name: String, priorities: Map<String, Int>) = RegionProvider(id, name, priorities)

    @Test
    fun `a country's services in its own order, each once, no ad tiers or channels`() {
        val movie = listOf(
            provider(8, "Netflix", mapOf("US" to 0, "HU" to 3)),
            provider(1796, "Netflix basic with Ads", mapOf("US" to 1)),
            provider(9, "Amazon Prime Video", mapOf("US" to 2)),
            provider(1825, "HBO Max Amazon Channel", mapOf("US" to 3)),
            provider(10, "Amazon Video", mapOf("US" to 0)),
            provider(2285, "JustWatch TV", mapOf("US" to 0)),
        )
        val tv = listOf(
            provider(15, "Hulu", mapOf("US" to 1)),
            provider(8, "Netflix", mapOf("US" to 5)),
        )
        val services = Subscriptions.servicesIn("US", movie, tv)
        assertEquals(listOf(8, 15, 9), services.map { it.providerId })
        assertEquals("Netflix", services.first().name)
        assertEquals(listOf(8), Subscriptions.choices(services, keep = emptySet(), limit = 1).map { it.providerId })
        // A service the user has stays on offer, even far down the country's list.
        assertEquals(listOf(8, 9), Subscriptions.choices(services, keep = setOf(9), limit = 1).map { it.providerId })
    }
}
