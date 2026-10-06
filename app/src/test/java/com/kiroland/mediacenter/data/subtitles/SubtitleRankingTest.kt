package com.kiroland.mediacenter.data.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleRankingTest {
    private fun offer(id: Long, release: String, downloads: Int, machine: Boolean = false) =
        SubtitleOffer(id, "hu", release, downloads, hearingImpaired = false, forced = false, machineTranslated = machine)

    @Test
    fun `matching release first, then human translations, then downloads`() {
        val offers = listOf(
            offer(1, "Charmed.S01E01.DVDRip.XviD-FoV", 900),
            offer(2, "Charmed.S01E01.1080p.AMZN.WEB-DL.DDP2.0.H.264-NTb", 50),
            offer(3, "Charmed S01E01 1080p WEB", 20, machine = true),
            offer(4, "Charmed S01E01 1080p WEB", 10),
        )
        val ranked = SubtitleRanking.rank(offers, "Charmed.S01E01.Something.Wicca.This.Way.Comes.1080p.AMZN.WEB-DL.DDP2.0.H.264-NTb.mkv")
        assertEquals(listOf(2L, 4L, 3L, 1L), ranked.map { it.fileId })
    }
}
