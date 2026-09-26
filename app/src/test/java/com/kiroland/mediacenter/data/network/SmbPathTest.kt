package com.kiroland.mediacenter.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPathTest {

    @Test
    fun `parses host, share and path`() {
        val p = SmbPath.parse("smb://DESKTOP-PC/filmek/Végső állomás (2000)/Final.Destination.mkv")!!
        assertEquals("DESKTOP-PC", p.host)
        assertEquals("filmek", p.share)
        assertEquals("Végső állomás (2000)/Final.Destination.mkv", p.path)
        assertEquals("Végső állomás (2000)\\Final.Destination.mkv", p.smbPath)
        assertEquals("Final.Destination.mkv", p.name)
        assertEquals("smb://DESKTOP-PC/filmek/Végső állomás (2000)/Final.Destination.mkv", p.uri)
    }

    @Test
    fun `share root and children`() {
        val root = SmbPath.parse("smb://192.168.1.10/Megosztott/")!!
        assertEquals("", root.path)
        assertEquals("Megosztott", root.name)
        assertEquals("smb://192.168.1.10/Megosztott", root.uri)
        assertEquals("smb://192.168.1.10/Megosztott/a/b.mkv", root.child("a").child("b.mkv").uri)
    }

    @Test
    fun `rejects what is not a share path`() {
        assertNull(SmbPath.parse("/storage/1234/Filmek"))
        assertNull(SmbPath.parse("smb://host"))
        assertNull(SmbPath.parse("smb://bad host/share"))
        assertNull(SmbPath.parse("smb://host/share/../other"))
        assertTrue(SmbPath.isSmb("SMB://host/share"))
        assertFalse(SmbPath.isHost("-host"))
    }
}
