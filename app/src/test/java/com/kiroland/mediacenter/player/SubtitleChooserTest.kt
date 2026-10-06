package com.kiroland.mediacenter.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleChooserTest {

    private val huFull = TextTrackOption(0, 0, "hu", forced = false)
    private val huForced = TextTrackOption(1, 0, "hun", forced = true)
    private val en = TextTrackOption(2, 0, "en", forced = false)

    @Test
    fun `hungarian audio picks the forced hungarian track only`() {
        assertEquals(SubtitleChoice.Select(huForced), SubtitleChooser.choose("hu", listOf(huFull, huForced, en)))
    }

    @Test
    fun `hungarian audio without forced track turns subtitles off`() {
        assertEquals(SubtitleChoice.Off, SubtitleChooser.choose("hun", listOf(huFull, en)))
    }

    @Test
    fun `foreign audio picks the full hungarian track`() {
        assertEquals(SubtitleChoice.Select(huFull), SubtitleChooser.choose("en", listOf(huForced, huFull, en)))
    }

    @Test
    fun `foreign audio falls back to forced hungarian, then leaves the default alone`() {
        assertEquals(SubtitleChoice.Select(huForced), SubtitleChooser.choose("en", listOf(huForced, en)))
        assertEquals(SubtitleChoice.Keep, SubtitleChooser.choose("en", listOf(en)))
        assertEquals(SubtitleChoice.Keep, SubtitleChooser.choose(null, emptyList()))
    }

    @Test
    fun `follows the app language`() {
        val enFull = TextTrackOption(3, 0, "eng", forced = false)
        val enForced = TextTrackOption(4, 0, "en", forced = true)
        val deFull = TextTrackOption(5, 0, "ger", forced = false)
        val all = listOf(huFull, enFull, enForced, deFull)
        assertEquals(SubtitleChoice.Select(enForced), SubtitleChooser.choose("en", all, preferred = "en"))
        assertEquals(SubtitleChoice.Select(enFull), SubtitleChooser.choose("hu", all, preferred = "en"))
        assertEquals(SubtitleChoice.Select(deFull), SubtitleChooser.choose("en", all, preferred = "de"))
        assertEquals(SubtitleChoice.Off, SubtitleChooser.choose("deu", all, preferred = "de"))
    }
}
