package com.kiroland.mediacenter.media.parse

import com.kiroland.mediacenter.media.parse.ParsedMedia.Episode
import com.kiroland.mediacenter.media.parse.ParsedMedia.Movie
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaNameParserTest {

    private fun parse(name: String, vararg dirs: String) = MediaNameParser.parse(name, dirs.toList())

    @Test
    fun `scene style movies`() {
        assertEquals(Movie("Interstellar", 2014), parse("Interstellar.2014.1080p.BluRay.x264-SPARKS.mkv"))
        assertEquals(Movie("Dune Part Two", 2024), parse("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX.mkv"))
        assertEquals(Movie("The Matrix", 1999), parse("The Matrix (1999) [1080p].mp4"))
        assertEquals(Movie("Blade Runner 2049", 2017), parse("Blade_Runner_2049_2017_HUN_ENG.mkv"))
        assertEquals(Movie("Mad Max Fury Road", 2015), parse("Mad.Max.Fury.Road.2015.1080p.HMAX.WEB-DL.mkv"))
    }

    @Test
    fun `movies without year stop at the first quality token`() {
        assertEquals(Movie("Life", null), parse("Life.2160p.remux-trinity.mkv"))
        assertEquals(Movie("Amelie", null), parse("Amelie.HUN.DVDRip.XviD.avi"))
        assertEquals(Movie("Some Home Video", null), parse("Some Home Video.mp4"))
    }

    @Test
    fun `numeric titles keep their number`() {
        assertEquals(Movie("1917", 2019), parse("1917.2019.1080p.mkv"))
        assertEquals(Movie("2001 A Space Odyssey", 1968), parse("2001.A.Space.Odyssey.1968.mkv"))
    }

    @Test
    fun `folder name is used when it knows more than the file`() {
        assertEquals(Movie("Interstellar", 2014), parse("movie.mkv", "Interstellar (2014)"))
        assertEquals(Movie("Interstellar", 2014), parse("interstellar-sample-cut.mkv", "Interstellar (2014)"))
        assertEquals(Movie("Inception", 2010), parse("Inception.2010.mkv", "Random folder"))
    }

    @Test
    fun `episodes from file names`() {
        assertEquals(Episode("Breaking Bad", 2, 5, null, null), parse("Breaking.Bad.S02E05.1080p.mkv"))
        assertEquals(Episode("The Office US", 3, 12, null, null), parse("The.Office.US.s03e12.720p.HDTV.mkv"))
        assertEquals(Episode("Doctor Who", 1, 1, null, 2005), parse("Doctor.Who.2005.S01E01.Rose.mkv"))
        assertEquals(Episode("Friends", 2, 5, null, null), parse("Friends 2x05 The One with Five Steaks.avi"))
        assertEquals(Episode("Show", 1, 1, 2, null), parse("Show.S01E01E02.mkv"))
        assertEquals(Episode("Show", 1, 1, 2, null), parse("Show - S01E01-E02 - Pilot.mkv"))
    }

    @Test
    fun `episode code only, show taken from folders`() {
        assertEquals(Episode("Breaking Bad", 2, 5, null, null), parse("S02E05.mkv", "Breaking Bad", "Season 2"))
    }

    @Test
    fun `episodes from season folders`() {
        assertEquals(Episode("Breaking Bad", 2, 5, null, null), parse("05 - Breakage.mkv", "Breaking Bad", "Season 2"))
        assertEquals(Episode("Barátok közt", 1, 5, null, null), parse("E05.mkv", "Barátok közt", "1. évad"))
        assertEquals(Episode("Dark", 3, 8, null, 2017), parse("Episode 8.mkv", "Dark (2017)", "S3"))
        assertEquals(Episode("Mad Men", 4, 2, null, null), parse("2. rész.mkv", "Mad Men", "Évad 4"))
    }

    @Test
    fun `real library layout`() {
        assertEquals(
            Movie("A Simple Favor", 2018),
            parse("gs88-a.simple.favor.mkv", "A.Simple.Favor.2018.CUSTOM.1080p.UHD.BluRay.DD5.1.x264.HUN-GS88"),
        )
        assertEquals(
            Movie("Final Destination", 2000),
            parse("sadu-final.destination.mkv", "Final.Destination.2000.1080p.Retail.BluRay.x264.Hun-Sadu"),
        )
        assertEquals(
            Movie("Life", 2017),
            parse("life.2160p.remux-trinity.mkv", "Life.2017.2160p.REMUX.UHD.BluRay.TrueHD.Atmos.7.1.HEVC.HuN-TRiNiTY"),
        )
        assertEquals(
            Movie("Moonfall", 2022),
            parse("Moonfall.2022.2160p.UHD.BluRay.TrueHD.Atmos.7.1.DoVi-HDR.x265.HuN-TRiNiTY.mkv", "Moonfall.2022.2160p.UHD.BluRay.TrueHD.Atmos.7.1.DoVi-HDR.x265.HuN-TRiNiTY"),
        )
        assertEquals(
            Episode("Charmed", 2, 10, null, null),
            parse("Charmed S02E10 1080p.mkv", "Charmed", "Charmed S02 1080p"),
        )
        assertEquals(
            Episode("Charmed", 1, 7, null, 1998),
            parse("Charmed.S01E07.1080p.BluRay.x264.HuN-TRiNiTY.mkv", "Charmed", "Charmed.1998.S01.1080p.BluRay.DD2.0.x264.HuN-TRiNiTY"),
        )
        assertEquals(
            Episode("Silo", 3, 10, null, null),
            parse("Silo.S03E10.Troy.2160p.ATVP.WEB-DL.DDP5.1.Atmos.DoVi.HDR.H.265-playWEB.mkv", "Silo.S03.2160p.ATVP.WEB-DL.DDP5.1.Atmos.DoVi.HDR.H.265-playWEB"),
        )
        assertEquals(
            Episode("The Walking Dead Dead City", 2, 4, null, null),
            parse("The.Walking.Dead.Dead.City.S02E04.1080p.AMZN.WEB-DL.DDP5.1.H.264.HUN.ENG-PTHD.mkv", "TWD-Dead City S2"),
        )
    }

    @Test
    fun `series keys group spelling variants`() {
        assertEquals(MediaNameParser.seriesKey("The Office US"), MediaNameParser.seriesKey("the.office (US)"))
        assertEquals("barátokközt", MediaNameParser.seriesKey("Barátok közt"))
    }
}
