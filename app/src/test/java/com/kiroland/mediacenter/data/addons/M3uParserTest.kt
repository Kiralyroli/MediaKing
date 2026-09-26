package com.kiroland.mediacenter.data.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    @Test
    fun `extended playlist with attributes and guide`() {
        val playlist = M3uParser.parse(
            """
            #EXTM3U x-tvg-url="https://example.org/guide.xml.gz"
            #EXTINF:-1 tvg-id="dw.de" tvg-logo="https://example.org/dw.png" group-title="Hírek",DW English
            #EXTVLCOPT:http-user-agent=Test
            https://example.org/dw/index.m3u8
            #EXTINF:-1,Plain Channel
            http://example.org/plain.m3u8
            """.trimIndent(),
        )
        assertEquals("https://example.org/guide.xml.gz", playlist.guideUrl)
        assertEquals(2, playlist.channels.size)
        val dw = playlist.channels[0]
        assertEquals("DW English", dw.name)
        assertEquals("dw.de", dw.id)
        assertEquals("dw.de", dw.epgId)
        assertEquals("Hírek", dw.group)
        assertEquals("https://example.org/dw.png", dw.logo)
        assertEquals("https://example.org/dw/index.m3u8", dw.url)
        assertNull(playlist.channels[1].epgId)
    }

    @Test
    fun `names with commas, junk lines and duplicates`() {
        val playlist = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="a",News, Weather & Sport
            https://x/a.m3u8
            #EXTINF:-1 tvg-id="a" group-title="Other",News again
            https://x/a2.m3u8
            #EXTINF:-1,Not a stream
            rtmp://x/y
            garbage line
            """.trimIndent(),
        )
        assertEquals(listOf("a", "a~2"), playlist.channels.map { it.id })
        assertEquals(listOf("News, Weather & Sport", "News again"), playlist.channels.map { it.name })
        assertEquals("Other", playlist.channels[1].group)
    }
}
