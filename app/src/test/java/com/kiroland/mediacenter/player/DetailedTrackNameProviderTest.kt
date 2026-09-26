package com.kiroland.mediacenter.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailedTrackNameProviderTest {

    private fun name(build: Format.Builder.() -> Unit) =
        DetailedTrackNameProvider.getTrackName(Format.Builder().apply(build).build())

    @Test
    fun `embedded forced srt subtitle`() {
        assertEquals(
            "Magyar · Forced · SRT · Kényszerített",
            name {
                setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                setLanguage("hu")
                setLabel("Forced")
                setSelectionFlags(C.SELECTION_FLAG_FORCED)
            },
        )
    }

    @Test
    fun `forced only in the track title, as in real release mkvs`() {
        assertEquals(
            "Magyar · srt.hunsub.forced · SRT · Kényszerített",
            name {
                setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                setLanguage("hun")
                setLabel("srt.hunsub.forced")
            },
        )
    }

    @Test
    fun `image based pgs sdh subtitle`() {
        assertEquals(
            "Angol · PGS (képalapú) · SDH",
            name {
                setSampleMimeType(MimeTypes.APPLICATION_PGS)
                setLanguage("eng")
                setRoleFlags(C.ROLE_FLAG_CAPTION)
            },
        )
    }

    @Test
    fun `external file parsed during extraction keeps its original type`() {
        assertEquals(
            "Magyar · ASS/SSA · külső fájl: Film.hu.ass",
            name {
                setId("1:${DetailedTrackNameProvider.EXTERNAL_ID_PREFIX}Film.hu.ass")
                setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                setCodecs(MimeTypes.TEXT_SSA)
                setLanguage("hu")
                setLabel("Film.hu.ass")
            },
        )
    }

    @Test
    fun `unknown language subtitle`() {
        assertEquals("Ismeretlen nyelv · WebVTT", name { setSampleMimeType(MimeTypes.TEXT_VTT) })
    }

    @Test
    fun `audio track with codec channels and bitrate`() {
        assertEquals(
            "Angol · Dolby TrueHD · 7.1 · 4608 kbps · Kommentár",
            name {
                setSampleMimeType(MimeTypes.AUDIO_TRUEHD)
                setLanguage("en")
                setChannelCount(8)
                setAverageBitrate(4_608_000)
                setRoleFlags(C.ROLE_FLAG_COMMENTARY)
            },
        )
    }

    @Test
    fun `live stream original audio tagged mul uses its title`() {
        assertEquals(
            "eredeti · AAC · Sztereó",
            name {
                setSampleMimeType(MimeTypes.AUDIO_AAC)
                setLanguage("mul")
                setLabel("eredeti")
                setChannelCount(2)
            },
        )
    }

    @Test
    fun `channel layouts`() {
        assertEquals("Sztereó", DetailedTrackNameProvider.channelLayout(2))
        assertEquals("5.1", DetailedTrackNameProvider.channelLayout(6))
        assertEquals(null, DetailedTrackNameProvider.channelLayout(Format.NO_VALUE))
    }
}
