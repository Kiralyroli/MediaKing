package com.kiroland.mediacenter.data.transfer

import com.kiroland.mediacenter.util.TestStrings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class UploadStoreTest {
    init {
        TestStrings.install()
    }


    @get:Rule
    val tmp = TemporaryFolder()

    private val root: File by lazy { tmp.newFolder("drive", "Filmek") }
    private val store by lazy { UploadStore(allowedRoots = { listOf(root) }, reserveBytes = 0) }

    private fun UploadStore.chunk(dir: File, name: String, data: ByteArray, offset: Int, length: Int) =
        append(dir, name, offset.toLong(), data.size.toLong(), length.toLong(), data.inputStream(offset, length))

    @Test
    fun `chunks are assembled and renamed when complete`() {
        val data = Random.nextBytes(10_000)
        val first = store.chunk(root, "Film.mkv", data, 0, 4_000)
        assertEquals(UploadResult.Accepted(4_000, done = false, file = File(root, "Film.mkv")), first)
        assertFalse(File(root, "Film.mkv").exists())
        assertTrue("part file is hidden from the scanner", File(root, ".Film.mkv.mcpart").exists())

        val second = store.chunk(root, "Film.mkv", data, 4_000, 6_000) as UploadResult.Accepted
        assertTrue(second.done)
        assertArrayEquals(data, File(root, "Film.mkv").readBytes())
        assertFalse(File(root, ".Film.mkv.mcpart").exists())
    }

    @Test
    fun `interrupted upload resumes from the part file size`() {
        val data = Random.nextBytes(5_000)
        store.chunk(root, "a.mkv", data, 0, 2_000)
        assertEquals(UploadStatus(received = 2_000, exists = false), store.status(root, "a.mkv"))

        // A client that restarts from 0 is told where to continue.
        assertEquals(UploadResult.OffsetMismatch(2_000), store.chunk(root, "a.mkv", data, 0, 1_000))
        assertTrue((store.chunk(root, "a.mkv", data, 2_000, 3_000) as UploadResult.Accepted).done)
        assertArrayEquals(data, File(root, "a.mkv").readBytes())
    }

    @Test
    fun `existing files are never overwritten`() {
        File(root, "kept.mkv").writeText("original")
        val result = store.chunk(root, "kept.mkv", ByteArray(10), 0, 10)
        assertEquals(UploadResult.AlreadyExists, result)
        assertEquals("original", File(root, "kept.mkv").readText())
    }

    @Test
    fun `refuses to start when the drive is too full`() {
        val tight = UploadStore(allowedRoots = { listOf(root) }, reserveBytes = 100, freeSpace = { 1_000 })
        val result = tight.append(root, "big.mkv", 0, 5_000, 10, ByteArray(10).inputStream())
        assertEquals(UploadResult.NotEnoughSpace(5_000, 900), result)
        assertFalse(File(root, ".big.mkv.mcpart").exists())
    }

    @Test
    fun `paths cannot escape the allowed roots`() {
        assertNotNull(store.resolveDir(root.path))
        assertNotNull(store.resolveDir(root.path, listOf("Charmed", "Season 1")))
        assertNull(store.resolveDir(root.path, listOf("..", "..")))
        assertNull(store.resolveDir(File(root, "../..").path))
        assertNull(store.resolveDir(tmp.root.path))
        assertNull(store.resolveDir(root.path + "-evil"))
    }

    @Test
    fun `file names must be single safe segments`() {
        listOf("Film.2014.mkv", "Barátok közt S01E01.mkv", ".hidden").forEach { assertTrue(it, UploadStore.isSafeName(it)) }
        listOf("", "..", "a/b.mkv", "a\\b", "what?.mkv", "name.", "name ", "tab\t.mkv").forEach {
            assertFalse(it, UploadStore.isSafeName(it))
        }
    }

    @Test
    fun `a file where a folder should be gives a clear error`() {
        // What a dragged-in folder used to leave behind: an empty file with the folder's name.
        File(root, "Show.S03").writeBytes(ByteArray(0))
        val result = store.chunk(File(root, "Show.S03"), "S03E01.mkv", ByteArray(10), 0, 10)
        assertEquals(UploadResult.Rejected("Mappa helyett fájl van ezen a néven: Show.S03"), result)
    }

    @Test
    fun `files and whole folders can be deleted, with a preview first`() {
        File(root, "old.mkv").writeBytes(ByteArray(100))
        File(root, "Show/Season 1").mkdirs()
        File(root, "Show/Season 1/e1.mkv").writeBytes(ByteArray(30))
        File(root, "Show/Season 1/e2.mkv").writeBytes(ByteArray(20))

        assertEquals(DeletionPreview(isDirectory = false, files = 1, bytes = 100), store.inspect(root, "old.mkv"))
        assertEquals(DeletionPreview(isDirectory = true, files = 2, bytes = 50), store.inspect(root, "Show"))

        assertTrue(store.delete(root, "old.mkv"))
        assertTrue(store.delete(root, "Show"))
        assertFalse(File(root, "old.mkv").exists())
        assertFalse(File(root, "Show").exists())
        assertFalse("already gone", store.delete(root, "old.mkv"))
    }

    @Test
    fun `roots, library folders and their parents are never deleted`() {
        val drive = root.parentFile!!
        val library = File(drive, "Media/Filmek").apply { mkdirs() }
        File(library, "keep.mkv").writeBytes(ByteArray(1))
        val guarded = UploadStore(allowedRoots = { listOf(drive) }, protectedFolders = { listOf(library) }, reserveBytes = 0)

        assertNull(guarded.inspect(drive.parentFile!!, drive.name))
        assertFalse("the drive itself", guarded.delete(drive.parentFile!!, drive.name))
        assertFalse("the library folder", guarded.delete(File(drive, "Media"), "Filmek"))
        assertFalse("a folder containing it", guarded.delete(drive, "Media"))
        assertFalse("climbing out", guarded.delete(library, ".."))
        assertTrue(File(library, "keep.mkv").exists())
        assertTrue("files inside stay deletable", guarded.delete(library, "keep.mkv"))
    }

    @Test
    fun `cancelled uploads can be discarded`() {
        store.chunk(root, "x.mkv", ByteArray(100), 0, 50)
        assertTrue(store.discard(root, "x.mkv"))
        assertEquals(UploadStatus(0, exists = false), store.status(root, "x.mkv"))
    }
}
