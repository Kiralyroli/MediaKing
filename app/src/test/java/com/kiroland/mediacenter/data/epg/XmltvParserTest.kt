package com.kiroland.mediacenter.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvParserTest {

    private val guide = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="m1.hu"><display-name>M1</display-name></channel>
          <programme start="20260926180000 +0200" stop="20260926190000 +0200" channel="m1.hu">
            <title lang="en">News</title><title lang="hu">Híradó</title>
            <desc lang="hu">Esti hírek.</desc>
          </programme>
          <programme start="20260926190000 +0200" stop="20260926200000 +0200" channel="m1.hu">
            <title>Időjárás</title>
          </programme>
          <programme start="20260927090000 +0200" stop="20260927100000 +0200" channel="m1.hu">
            <title>Too late</title>
          </programme>
          <programme start="20260926180000 +0200" stop="20260926190000 +0200" channel="other.hu">
            <title>Not wanted</title>
          </programme>
        </tv>
    """.trimIndent()

    private val at1830 = XmltvParser.time("20260926183000 +0200")

    @Test
    fun `keeps wanted channels inside the window, hungarian titles first`() {
        val result = XmltvParser.parse(
            guide.byteInputStream(),
            wanted = setOf("m1.hu"),
            from = at1830 - 3_600_000,
            to = at1830 + 12 * 3_600_000,
        )
        assertEquals(setOf("m1.hu"), result.keys)
        assertEquals(listOf("Híradó", "Időjárás"), result.getValue("m1.hu").map { it.title })
        assertEquals("Esti hírek.", result.getValue("m1.hu").first().description)
    }

    @Test
    fun `now and next`() {
        val list = XmltvParser.parse(guide.byteInputStream(), setOf("m1.hu"), 0, Long.MAX_VALUE).getValue("m1.hu")
        val (now, next) = XmltvParser.nowNext(list, at1830)
        assertEquals("Híradó", now?.title)
        assertEquals("Időjárás", next?.title)
        // Between programmes: nothing now, the next one after.
        val (gapNow, gapNext) = XmltvParser.nowNext(list, XmltvParser.time("20260926230000 +0200"))
        assertNull(gapNow)
        assertEquals("Too late", gapNext?.title)
    }

    @Test
    fun `time offsets`() {
        assertEquals(XmltvParser.time("20260926160000 +0000"), XmltvParser.time("20260926180000 +0200"))
        assertEquals(XmltvParser.time("20260926160000 +0000"), XmltvParser.time("20260926160000"))
        assertEquals(0L, XmltvParser.time("garbage"))
    }
}
