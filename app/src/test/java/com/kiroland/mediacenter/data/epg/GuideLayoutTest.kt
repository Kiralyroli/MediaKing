package com.kiroland.mediacenter.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideLayoutTest {

    private val min = 60_000L
    private fun p(start: Int, stop: Int, title: String = "$start") = Programme(start * min, stop * min, title)

    @Test
    fun `fills holes and clips to the window`() {
        val blocks = GuideLayout.blocks(listOf(p(-30, 20, "a"), p(40, 90, "b"), p(90, 200, "c")), from = 0, to = 120 * min)
        assertEquals(listOf(0L, 20L, 40L, 90L), blocks.map { it.start / min })
        assertEquals(listOf(20L, 40L, 90L, 120L), blocks.map { it.end / min })
        assertEquals(listOf("a", null, "b", "c"), blocks.map { it.programme?.title })
    }

    @Test
    fun `overlapping programmes do not overlap on screen`() {
        val blocks = GuideLayout.blocks(listOf(p(0, 60, "a"), p(50, 100, "b")), from = 0, to = 100 * min)
        assertEquals(listOf(0L to 60L, 60L to 100L), blocks.map { it.start / min to it.end / min })
    }

    @Test
    fun `an empty schedule is one empty block`() {
        val blocks = GuideLayout.blocks(emptyList(), from = 0, to = 60 * min)
        assertEquals(1, blocks.size)
        assertNull(blocks[0].programme)
        assertEquals(60f, blocks[0].minutes)
    }

    @Test
    fun `window starts on a local half hour, at least half an hour back`() {
        val offset = 2 * 60 * min // CEST
        // 15:54 local = 13:54 UTC → window at 15:00 local = 13:00 UTC.
        assertEquals(13 * 60 * min, GuideLayout.windowStart(13 * 60 * min + 54 * min, offset))
        // 16:30 local exactly → 16:00 local.
        assertEquals(14 * 60 * min, GuideLayout.windowStart(14 * 60 * min + 30 * min, offset))
    }
}
