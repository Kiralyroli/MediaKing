package com.kiroland.mediacenter.data.transfer

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.random.Random

/** End-to-end over real HTTP: the actual Ktor server on a local port, driven like the web page does. */
class TransferServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var server: TransferServer
    private var port = 0
    private val done = mutableListOf<File>()

    private val host = object : TransferHost {
        override val pairingCode = "K7M2QP9X"
        override val deviceName = "Test TV"
        override fun roots() = listOf(RootDto(root.path, "Filmek", "library", 0, 0))
        override fun onChunk(file: File, total: Long, receivedBefore: Long, bytes: Long) = Unit
        override fun onFileDone(file: File) { done += file }
        override fun onDeleted(file: File) = Unit
        val installed = mutableListOf<AddonDto>()
        override fun addons() = installed.toList()
        override fun installAddon(text: String) = com.kiroland.mediacenter.data.addons.AddonParser.parse(text)
            .map { AddonDto(it.id, it.name, it.version, it.description, it.channels.size) }
            .onSuccess { installed += it }
        override suspend fun installAddonFromUrl(url: String) = Result.failure<AddonDto>(IllegalStateException("offline"))
        override fun removeAddon(id: String) = installed.removeIf { it.id == id }
    }

    @Before
    fun setUp() {
        root = tmp.newFolder("Filmek")
        server = TransferServer(host, UploadStore(allowedRoots = { listOf(root) }, reserveBytes = 0)) { "<html>ok</html>".toByteArray() }
        port = server.start(18080..18180)
    }

    @After
    fun tearDown() = server.stop()

    private data class Response(val status: Int, val body: String)

    private fun request(method: String, path: String, code: String? = "k7m2-qp9x", body: ByteArray? = null): Response {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        // Each test starts a new server, often on the same port: never reuse a pooled connection.
        connection.setRequestProperty("Connection", "close")
        code?.let { connection.setRequestProperty("X-Pairing-Code", it) }
        if (body != null) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }
        val status = connection.responseCode
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        return Response(status, stream?.bufferedReader()?.readText().orEmpty())
    }

    private fun q(vararg pairs: Pair<String, Any>) =
        pairs.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v.toString(), "UTF-8") }

    @Test
    fun `page is public, api needs the pairing code`() {
        assertEquals(Response(200, "<html>ok</html>"), request("GET", "/", code = null))
        assertEquals(401, request("GET", "/api/info", code = null).status)
        assertEquals(401, request("GET", "/api/info", code = "WRONG123").status)
        val info = request("GET", "/api/info")
        assertEquals(200, info.status)
        assertTrue(info.body, info.body.contains("\"device\":\"Test TV\""))
    }

    @Test
    fun `chunked upload with a resume in the middle`() {
        val data = Random.nextBytes(3_000_000)
        val base = "/api/upload?" + q("dir" to root.path, "rel" to "Új sorozat/1. évad", "name" to "S01E01.mkv")

        val first = request("PUT", "$base&offset=0&total=${data.size}", body = data.copyOfRange(0, 1_000_000))
        assertEquals(200, first.status)
        assertTrue(first.body.contains("\"done\":false"))

        // The browser was closed; a fresh page asks where to continue.
        val status = request("GET", "/api/upload/status?" + q("dir" to root.path, "rel" to "Új sorozat/1. évad", "name" to "S01E01.mkv"))
        assertTrue(status.body, status.body.contains("\"received\":1000000"))

        // A stale offset is corrected rather than corrupting the file.
        val stale = request("PUT", "$base&offset=0&total=${data.size}", body = data.copyOfRange(0, 1_000))
        assertEquals(409, stale.status)
        assertTrue(stale.body.contains("\"received\":1000000"))

        val last = request("PUT", "$base&offset=1000000&total=${data.size}", body = data.copyOfRange(1_000_000, data.size))
        assertEquals(200, last.status)
        assertTrue(last.body.contains("\"done\":true"))

        val file = File(root, "Új sorozat/1. évad/S01E01.mkv")
        assertArrayEquals(data, file.readBytes())
        assertEquals(listOf(file.canonicalFile), done.map { it.canonicalFile })
    }

    @Test
    fun `writes outside the allowed folders are refused`() {
        val outside = tmp.newFolder("elsewhere")
        val escape = request("PUT", "/api/upload?" + q("dir" to outside.path, "name" to "x.bin", "offset" to 0, "total" to 3), body = byteArrayOf(1, 2, 3))
        assertEquals(403, escape.status)
        val climb = request("PUT", "/api/upload?" + q("dir" to root.path, "rel" to "../..", "name" to "x.bin", "offset" to 0, "total" to 3), body = byteArrayOf(1, 2, 3))
        assertEquals(403, climb.status)
        assertFalse(File(outside, "x.bin").exists())
    }

    @Test
    fun `delete needs the code and cannot touch the root`() {
        File(root, "Régi.mkv").writeBytes(ByteArray(5))
        val del = "/api/delete?" + q("dir" to root.path, "name" to "Régi.mkv")
        assertEquals(401, request("POST", del, code = null).status)
        assertTrue(request("GET", "/api/stat?" + q("dir" to root.path, "name" to "Régi.mkv")).body.contains("\"bytes\":5"))
        assertEquals(200, request("POST", del).status)
        assertFalse(File(root, "Régi.mkv").exists())
        assertEquals(403, request("POST", "/api/delete?" + q("dir" to root.parent, "name" to root.name)).status)
        assertTrue(root.exists())
    }

    @Test
    fun `add-ons are installed, validated and removed`() {
        val good = """{"id":"demo","name":"Demo","channels":[{"id":"a","name":"A","url":"https://x/a.m3u8"}]}"""
        assertEquals(401, request("POST", "/api/addons", code = null, body = good.toByteArray()).status)
        val ok = request("POST", "/api/addons", body = good.toByteArray())
        assertEquals(200, ok.status)
        assertTrue(ok.body, ok.body.contains("\"channels\":1"))
        val bad = request("POST", "/api/addons", body = "{}".toByteArray())
        assertEquals(400, bad.status)
        assertTrue(request("GET", "/api/addons").body.contains("\"id\":\"demo\""))
        assertEquals(200, request("DELETE", "/api/addons?id=demo").status)
        assertEquals(404, request("DELETE", "/api/addons?id=demo").status)
    }

    @Test
    fun `listing hides part files but reports them as unfinished`() {
        request("PUT", "/api/upload?" + q("dir" to root.path, "name" to "Film.mkv", "offset" to 0, "total" to 10), body = ByteArray(4))
        File(root, "Kész.mkv").writeBytes(ByteArray(7))
        val listing = request("GET", "/api/list?" + q("dir" to root.path)).body
        assertTrue(listing, listing.contains("\"files\":[{\"name\":\"Kész.mkv\",\"size\":7}]"))
        assertTrue(listing, listing.contains("\"partials\":[{\"name\":\"Film.mkv\",\"received\":4}]"))
    }
}
