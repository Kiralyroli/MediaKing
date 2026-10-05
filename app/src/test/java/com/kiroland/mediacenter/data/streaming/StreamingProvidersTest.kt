package com.kiroland.mediacenter.data.streaming

import com.kiroland.mediacenter.data.metadata.tmdb.CountryProviders
import com.kiroland.mediacenter.data.metadata.tmdb.ProviderEntry
import com.kiroland.mediacenter.data.streaming.StreamingProviders.OpenStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StreamingProvidersTest {

    private fun entry(id: Int, name: String, priority: Int) = ProviderEntry(id, name, "/$id.png", priority)

    @Test
    fun `one entry per provider, subscriptions first`() {
        // Interstellar in Hungary, as TMDB lists it.
        val offers = StreamingProviders.merge(
            CountryProviders(
                flatrate = listOf(entry(1899, "HBO Max", 28)),
                rent = listOf(entry(2, "Apple TV Store", 5), entry(35, "Rakuten TV", 26), entry(10, "Amazon Video", 37)),
                buy = listOf(entry(2, "Apple TV Store", 5), entry(3, "Google Play Movies", 6), entry(10, "Amazon Video", 37)),
            ),
        )
        assertEquals(listOf("HBO Max", "Apple TV Store", "Rakuten TV", "Amazon Video", "Google Play Movies"), offers.map { it.name })
        assertEquals(setOf(Offer.RENT, Offer.BUY), offers[1].offers)
        assertEquals(Offer.RENT, offers[1].best)
        assertEquals(Offer.BUY, offers.last().best)
        assertEquals("https://image.tmdb.org/t/p/w154/1899.png", offers.first().logoUrl)
    }

    @Test
    fun `free and ad-supported count as free`() {
        val offers = StreamingProviders.merge(CountryProviders(ads = listOf(entry(2695, "MediaKlikk", 39))))
        assertEquals(Offer.FREE, offers.single().best)
    }

    @Test
    fun `an installed app is opened, its search tried first`() {
        // Prime Video takes the title from its search link (tested on the TV).
        val prime = StreamingProviders.apps.getValue(119)
        assertTrue(prime.searchFillsTitle)
        assertEquals(
            listOf(
                OpenStep.LinkInApp("com.amazon.amazonvideo.livingroom", "https://app.primevideo.com/search?phrase=D%C5%B1ne%3A+M%C3%A1sodik+r%C3%A9sz"),
                OpenStep.LaunchApp("com.amazon.amazonvideo.livingroom"),
            ),
            StreamingProviders.plan(prime, setOf("com.amazon.amazonvideo.livingroom"), "Dűne: Második rész"),
        )
        // HBO Max only opens its (empty) search page.
        val max = StreamingProviders.apps.getValue(1899)
        assertTrue(!max.searchFillsTitle)
        assertEquals(OpenStep.LinkInApp("com.wbd.stream", "https://play.hbomax.com/search"), StreamingProviders.plan(max, setOf("com.wbd.stream"), "x").first())
        // Netflix ignores search links on TV: just the app.
        val netflix = StreamingProviders.apps.getValue(8)
        assertEquals(listOf(OpenStep.LaunchApp("com.netflix.ninja")), StreamingProviders.plan(netflix, setOf("com.netflix.ninja"), "x"))
    }

    @Test
    fun `a missing app goes to the store, then the website`() {
        val max = StreamingProviders.apps.getValue(1899)
        assertEquals(
            listOf(OpenStep.StorePage("com.wbd.stream"), OpenStep.Website("https://www.hbomax.com/")),
            StreamingProviders.plan(max, emptySet(), "Interstellar"),
        )
    }

    @Test
    fun `every app package is declared in the manifest queries`() {
        // Without a <queries> entry Android 11+ reports the app as not installed.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val missing = StreamingProviders.allPackages.filterNot { manifest.contains("<package android:name=\"$it\" />") }
        assertTrue("Missing from <queries>: $missing", missing.isEmpty())
    }

    @Test
    fun `apps are searched by the original title, if it can be typed`() {
        assertEquals("Another Simple Favor", searchTitle("Another Simple Favor", "Még egy kis szívesség"))
        // Accents and punctuation are fine.
        assertEquals("Le Fabuleux Destin d'Amélie Poulain", searchTitle("Le Fabuleux Destin d'Amélie Poulain", "Amélie csodálatos élete"))
        assertEquals("Élősködők", searchTitle("기생충", "Élősködők"))
        assertEquals("Végső állomás", searchTitle(null, "Végső állomás"))
    }
}
