package com.kiroland.mediacenter.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class SubtitleLoaderTest {

    private val sample = "Árvíztűrő tükörfúrógép – őrült ÜLLŐ"

    @Test
    fun `utf8 without bom is kept`() {
        assertEquals(sample, SubtitleLoader.decode(sample.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `utf8 bom is stripped`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sample.toByteArray(Charsets.UTF_8)
        assertEquals(sample, SubtitleLoader.decode(bytes))
    }

    @Test
    fun `utf16 little endian with bom`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + sample.toByteArray(Charsets.UTF_16LE)
        assertEquals(sample, SubtitleLoader.decode(bytes))
    }

    @Test
    fun `windows-1250 is detected for legacy hungarian subtitles`() {
        val bytes = "Árvíztűrő tükörfúrógép őrült ÜLLŐ".toByteArray(Charset.forName("windows-1250"))
        assertEquals("Árvíztűrő tükörfúrógép őrült ÜLLŐ", SubtitleLoader.decode(bytes))
    }

    @Test
    fun `release folder subtitles with different names`() {
        assertEquals(
            SubtitleInfo("hu", forced = true, sdh = false),
            SubtitleLoader.describe("life.2160p.forced.hunsub-trinity", "life.2160p.remux-trinity", "hun"),
        )
        assertEquals(
            SubtitleInfo("hu", forced = false, sdh = false),
            SubtitleLoader.describe("sadu-final.destination.hunsub", "sadu-final.destination", "hundub.hunsub"),
        )
        assertEquals(
            SubtitleInfo("en", forced = false, sdh = true),
            SubtitleLoader.describe("Movie.2020.eng.SDH", "Movie.2020", null),
        )
        // "it" inside a title is not Italian.
        assertEquals(SubtitleInfo(null, false, false), SubtitleLoader.describe("It.Follows.subs", "other", null))
    }

    @Test
    fun `language suffixes`() {
        assertEquals("hu", SubtitleLoader.languageOf("Film.2014.hu", "Film.2014"))
        assertEquals("hu", SubtitleLoader.languageOf("Film.2014.hun", "Film.2014"))
        assertEquals("hu", SubtitleLoader.languageOf("Film.2014.Hungarian", "Film.2014"))
        assertEquals("en", SubtitleLoader.languageOf("Film.2014_eng", "Film.2014"))
        assertEquals("fr", SubtitleLoader.languageOf("Film.2014.fr", "Film.2014"))
        assertNull(SubtitleLoader.languageOf("Film.2014", "Film.2014"))
        assertNull(SubtitleLoader.languageOf("Film.2014.forced-signs", "Film.2014"))
    }
}
