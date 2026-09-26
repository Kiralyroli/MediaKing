package com.kiroland.mediacenter.media

import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.util.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTypeTest {

    @Test
    fun `classifies by extension case-insensitively`() {
        assertEquals(MediaType.VIDEO, MediaType.fromFileName("Breaking.Bad.S02E05.1080p.MKV"))
        assertEquals(MediaType.AUDIO, MediaType.fromFileName("song.flac"))
        assertEquals(MediaType.SUBTITLE, MediaType.fromFileName("Film.hu.srt"))
        assertEquals(MediaType.STREAM, MediaType.fromFileName("m4.strm"))
        assertEquals(MediaType.OTHER, MediaType.fromFileName("README"))
        assertEquals(MediaType.OTHER, MediaType.fromFileName("archive.zip"))
    }

    @Test
    fun `hides system and chkdsk entries`() {
        assertTrue(StorageRepository.isHidden(".mediaexplorer"))
        assertTrue(StorageRepository.isHidden("\$RECYCLE.BIN"))
        assertTrue(StorageRepository.isHidden("System Volume Information"))
        assertTrue(StorageRepository.isHidden("found.001"))
        assertFalse(StorageRepository.isHidden("Filmek"))
        assertFalse(StorageRepository.isHidden("found.it"))
    }

    @Test
    fun `formats sizes with hungarian decimal comma`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1,5 KB", formatBytes(1536))
        assertEquals("426 GB", formatBytes(426L * 1024 * 1024 * 1024))
        assertEquals("1,7 TB", formatBytes((1.72 * 1024 * 1024 * 1024 * 1024).toLong()))
    }
}
