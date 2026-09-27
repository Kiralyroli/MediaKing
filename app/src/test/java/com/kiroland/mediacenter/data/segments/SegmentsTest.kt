package com.kiroland.mediacenter.data.segments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class SegmentsTest {

    // --- a tiny EBML writer for synthetic files ---
    private fun id(v: Long): ByteArray {
        val n = when { v > 0xFFFFFF -> 4; v > 0xFFFF -> 3; v > 0xFF -> 2; else -> 1 }
        return ByteArray(n) { ((v shr (8 * (n - 1 - it))) and 0xFF).toByte() }
    }
    private fun size(n: Int): ByteArray =
        if (n < 0x3FFF) byteArrayOf((0x40 or (n shr 8)).toByte(), (n and 0xFF).toByte())
        else byteArrayOf(0x10, (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun el(id: Long, vararg children: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { children.forEach { write(it) } }.toByteArray()
        return id(id) + size(body.size) + body
    }
    private fun uint(id: Long, v: Long) = el(id, ByteArray(8) { ((v shr (8 * (7 - it))) and 0xFF).toByte() })
    private fun text(id: Long, s: String) = el(id, s.toByteArray())
    private fun atom(startMs: Long, name: String) =
        el(0xB6, uint(0x91, startMs * 1_000_000), el(0x80, text(0x85, name), text(0x437C, "eng")))
    private fun chapters(vararg atoms: ByteArray) = el(0x1043A770, el(0x45B9, *atoms))
    private val header = el(0x1A45DFA3, text(0x4282, "matroska"))

    private fun reader(bytes: ByteArray) = { offset: Long, length: Int ->
        bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + length))
    }

    @Test
    fun `chapters in the header, named like a streaming release`() {
        val file = header + el(
            0x18538067,
            el(0x1549A966, text(0x4D80, "mkvmerge")),
            chapters(atom(0, "Scene 1"), atom(1_428_000, "Intro"), atom(1_526_000, "Scene 2"), atom(3_558_000, "Credits")),
            el(0x1F43B675, uint(0xE7, 0)),
        )
        val chapters = MkvChapters.read(reader(file))!!
        assertEquals(listOf(0L, 1_428_000L, 1_526_000L, 3_558_000L), chapters.map { it.startMs })
        assertEquals(
            listOf(Segment(SegmentType.INTRO, 1_428_000, 1_526_000), Segment(SegmentType.CREDITS, 3_558_000, null)),
            ChapterSegments.fromChapters(chapters),
        )
    }

    @Test
    fun `chapters after the media data are found through the seek head`() {
        val cluster = el(0x1F43B675, uint(0xE7, 0))
        val chapterBlock = chapters(atom(0, "Recap"), atom(60_000, "Opening Credits"), atom(120_000, "Chapter 3"))
        // SeekHead is a fixed size, so the chapters' position can be written before it is known.
        fun seekHead(position: Long) = el(0x114D9B74, el(0x4DBB, el(0x53AB, id(0x1043A770)), uint(0x53AC, position)))
        val info = el(0x1549A966, text(0x4D80, "x"))
        val padding = ByteArray(300_000) // pushes the chapters past the 256 KB header read
        val before = seekHead(0).size + info.size + el(0xEC, padding).size + cluster.size
        val segmentBody = seekHead(before.toLong()) + info + el(0xEC, padding) + cluster + chapterBlock
        // A segment of unknown size, as some muxers write it.
        val unknownSize = byteArrayOf(0x01, -1, -1, -1, -1, -1, -1, -1)
        val file = header + id(0x18538067) + unknownSize + segmentBody
        val chapters = MkvChapters.read(reader(file))!!
        assertEquals(listOf("Recap", "Opening Credits", "Chapter 3"), chapters.map { it.name })
        assertEquals(
            listOf(SegmentType.RECAP, SegmentType.INTRO),
            ChapterSegments.fromChapters(chapters).map { it.type },
        )
    }

    @Test
    fun `numbered and timecode chapters say nothing`() {
        val names = listOf("Chapter 01", "00:10:01.559", "Scene 3", "Studio Logo")
        names.forEach { assertNull(it, ChapterSegments.typeOf(it)) }
        assertEquals(SegmentType.CREDITS, ChapterSegments.typeOf("End Credits"))
        assertEquals(SegmentType.INTRO, ChapterSegments.typeOf("Főcím"))
        assertEquals(SegmentType.PREVIEW, ChapterSegments.typeOf("Next Time"))
    }

    @Test
    fun `not a matroska file`() {
        assertNull(MkvChapters.read(reader(ByteArray(100) { 1 })))
    }

    @Test
    fun `segment bounds`() {
        val s = Segment(SegmentType.INTRO, 1000, 2000)
        assertEquals(listOf(false, true, true, false), listOf(999L, 1000L, 1999L, 2000L).map { s.contains(it) })
        assertEquals(true, Segment(SegmentType.CREDITS, 5000).contains(1_000_000))
    }
}
