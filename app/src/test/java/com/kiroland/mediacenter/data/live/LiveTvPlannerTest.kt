package com.kiroland.mediacenter.data.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvPlannerTest {

    private val m1 = PublicChannels.all.first { it.id == "m1" }
    private val m4 = PublicChannels.all.first { it.id == "m4" }

    @Test
    fun `mediaklikk opens the channel page first, the app itself as fallback`() {
        val steps = LiveTvPlanner.steps(m4, setOf(OfficialApps.MEDIAKLIKK, OfficialApps.M4_SPORT), hasBrowser = true)
        assertEquals(
            listOf(
                LaunchStep.OpenLinkInApp(OfficialApps.MEDIAKLIKK, "https://mediaklikk.hu/elo/mtv4live"),
                LaunchStep.LaunchApp(OfficialApps.MEDIAKLIKK),
            ),
            steps,
        )
    }

    @Test
    fun `sport channels fall back to the M4 Sport app`() {
        assertEquals(
            listOf(LaunchStep.LaunchApp(OfficialApps.M4_SPORT)),
            LiveTvPlanner.steps(m4, setOf(OfficialApps.M4_SPORT), hasBrowser = true),
        )
        // ...but the M4 Sport app is no use for M1.
        assertEquals(LaunchMode.WEBSITE, LiveTvPlanner.mode(m1, setOf(OfficialApps.M4_SPORT), hasBrowser = true))
    }

    @Test
    fun `without official apps the official website opens in the browser`() {
        // Current Médiaklikk builds are "not compatible" with the Android 10 target TV.
        assertEquals(
            listOf(LaunchStep.OpenWebsite("https://mediaklikk.hu/elo/mtv1live")),
            LiveTvPlanner.steps(m1, emptySet(), hasBrowser = true),
        )
    }

    @Test
    fun `the store is the last resort`() {
        assertEquals(listOf(LaunchStep.OpenStorePage(OfficialApps.MEDIAKLIKK)), LiveTvPlanner.steps(m1, emptySet(), hasBrowser = false))
        assertEquals(listOf(LaunchStep.OpenStorePage(OfficialApps.M4_SPORT)), LiveTvPlanner.steps(m4, emptySet(), hasBrowser = false))
    }

    @Test
    fun `tuner app is found among known makers`() {
        assertEquals("com.mediatek.wwtv.tvcenter", LiveTvPlanner.tunerApp(setOf("com.mediatek.wwtv.tvcenter", "com.android.tv")))
        assertNull(LiveTvPlanner.tunerApp(setOf("com.example.other")))
    }

    @Test
    fun `every channel links to mediaklikk over https`() {
        assertEquals(7, PublicChannels.all.size)
        assertTrue(PublicChannels.all.all { it.pageUrl.startsWith("https://mediaklikk.hu/elo/") })
    }
}
