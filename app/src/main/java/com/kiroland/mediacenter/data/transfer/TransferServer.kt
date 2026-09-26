package com.kiroland.mediacenter.data.transfer

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RootDto(val path: String, val name: String, val kind: String, val freeBytes: Long, val totalBytes: Long)

@Serializable
data class InfoDto(val device: String, val roots: List<RootDto>)

@Serializable
data class EntryDto(val name: String, val size: Long)

@Serializable
data class PartialDto(val name: String, val received: Long)

@Serializable
data class ListingDto(
    val path: String,
    val dirs: List<String>,
    val files: List<EntryDto>,
    val partials: List<PartialDto>,
    val freeBytes: Long,
)

@Serializable
data class StatusDto(val received: Long, val exists: Boolean)

@Serializable
data class ChunkDto(val received: Long, val done: Boolean)

@Serializable
data class PreviewDto(val isDirectory: Boolean, val files: Int, val bytes: Long)

@Serializable
data class ErrorDto(val error: String, val received: Long? = null, val retryAfterMs: Long? = null)

/** What the server needs from the app; implemented by [TransferRepository]. */
interface TransferHost {
    val pairingCode: String
    val deviceName: String
    fun roots(): List<RootDto>
    fun onChunk(file: File, total: Long, receivedBefore: Long, bytes: Long)
    fun onFileDone(file: File)
    fun onDeleted(file: File)
}

/**
 * The HTTP side of Wi-Fi uploads: serves the web page and a small JSON API.
 * Every /api call must carry the pairing code in the X-Pairing-Code header.
 */
class TransferServer(
    private val host: TransferHost,
    private val store: UploadStore,
    /** The upload page (assets/web/index.html in the app, a stub in tests). */
    private val page: () -> ByteArray,
) {
    private val json = Json { encodeDefaults = false }
    private val guard = PairingGuard()
    private var server: EmbeddedServer<*, *>? = null

    /** Starts on the first free port from [ports]; returns the port or throws if none could be bound. */
    fun start(ports: IntRange = 8080..8090): Int {
        var lastError: Exception? = null
        for (port in ports) {
            try {
                val candidate = embeddedServer(CIO, port = port, host = "0.0.0.0") { routing { routes() } }
                candidate.start(wait = false)
                server = candidate
                return port
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("Nincs szabad port (${ports.first}–${ports.last})", lastError)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }

    private fun io.ktor.server.routing.Routing.routes() {
        get("/") {
            val html = withContext(Dispatchers.IO) { page() }
            call.response.header("Cache-Control", "no-store")
            call.respondBytes(html, ContentType.Text.Html.withParameter("charset", "utf-8"))
        }

        get("/api/info") {
            if (!authorized(call)) return@get
            val roots = withContext(Dispatchers.IO) { host.roots() }
            call.respondJson(InfoDto(host.deviceName, roots))
        }

        get("/api/list") {
            if (!authorized(call)) return@get
            val dir = targetDir(call) ?: return@get
            val listing = withContext(Dispatchers.IO) { list(dir) }
            call.respondJson(listing)
        }

        post("/api/mkdir") {
            if (!authorized(call)) return@post
            val dir = targetDir(call) ?: return@post
            val name = call.parameters["name"].orEmpty().trim()
            val created = withContext(Dispatchers.IO) { store.createFolder(dir, name) }
            if (created == null) call.respondError(HttpStatusCode.BadRequest, "Érvénytelen mappanév")
            else call.respondJson(EntryDto(created.name, 0))
        }

        get("/api/upload/status") {
            if (!authorized(call)) return@get
            val dir = targetDir(call) ?: return@get
            val name = call.parameters["name"].orEmpty()
            val status = withContext(Dispatchers.IO) { store.status(dir, name) }
            call.respondJson(StatusDto(status.received, status.exists))
        }

        put("/api/upload") {
            // Any refusal reads the rest of the chunk first: answering while the browser is still sending
            // resets the connection, and the page would see a network error instead of our reply.
            val body = call.receiveChannel()
            if (!authorized(call, body)) return@put
            val dir = targetDir(call, body) ?: return@put
            val name = call.parameters["name"].orEmpty()
            val offset = call.parameters["offset"]?.toLongOrNull()
            val total = call.parameters["total"]?.toLongOrNull()
            val length = call.request.contentLength()
            if (offset == null || total == null || length == null) {
                body.discard()
                call.respondError(HttpStatusCode.BadRequest, "Hiányzó offset, total vagy Content-Length")
                return@put
            }
            val input = body.toInputStream()
            val file = File(dir, name)
            var sent = 0L
            val result = withContext(Dispatchers.IO) {
                store.append(dir, name, offset, total, length, input) { bytes ->
                    host.onChunk(file, total, offset + sent, bytes)
                    sent += bytes
                }
            }
            if (result !is UploadResult.Accepted) body.discard()
            when (result) {
                is UploadResult.Accepted -> {
                    if (result.done) host.onFileDone(result.file)
                    call.respondJson(ChunkDto(result.received, result.done))
                }
                is UploadResult.OffsetMismatch ->
                    call.respondError(HttpStatusCode.Conflict, "Eltérő pozíció", received = result.received)
                UploadResult.AlreadyExists ->
                    call.respondError(HttpStatusCode.Conflict, "Ilyen nevű fájl már van a mappában")
                is UploadResult.NotEnoughSpace ->
                    call.respondError(HttpStatusCode.InsufficientStorage, "Nincs elég hely a meghajtón")
                is UploadResult.Rejected -> call.respondError(HttpStatusCode.BadRequest, result.reason)
            }
        }

        get("/api/stat") {
            if (!authorized(call)) return@get
            val dir = targetDir(call) ?: return@get
            val preview = withContext(Dispatchers.IO) { store.inspect(dir, call.parameters["name"].orEmpty()) }
            if (preview == null) call.respondError(HttpStatusCode.NotFound, "Nem található, vagy nem törölhető")
            else call.respondJson(PreviewDto(preview.isDirectory, preview.files, preview.bytes))
        }

        post("/api/delete") {
            if (!authorized(call)) return@post
            val dir = targetDir(call) ?: return@post
            val name = call.parameters["name"].orEmpty()
            val deleted = withContext(Dispatchers.IO) { store.delete(dir, name) }
            if (!deleted) {
                call.respondError(HttpStatusCode.Forbidden, "Nem törölhető (védett mappa, vagy már nem létezik)")
                return@post
            }
            host.onDeleted(File(dir, name))
            call.respondJson(ChunkDto(0, true))
        }

        delete("/api/upload") {
            if (!authorized(call)) return@delete
            val dir = targetDir(call) ?: return@delete
            withContext(Dispatchers.IO) { store.discard(dir, call.parameters["name"].orEmpty()) }
            call.respondJson(ChunkDto(0, false))
        }
    }

    private suspend fun authorized(call: ApplicationCall, body: ByteReadChannel? = null): Boolean {
        val check = guard.check(host.pairingCode, call.request.headers["X-Pairing-Code"])
        if (check != PairingGuard.Check.Ok) body?.discard()
        return when (check) {
            PairingGuard.Check.Ok -> true
            PairingGuard.Check.Wrong -> {
                call.respondError(HttpStatusCode.Unauthorized, "Hibás párosítási kód")
                false
            }
            is PairingGuard.Check.Locked -> {
                call.respondError(HttpStatusCode.TooManyRequests, "Túl sok hibás kód, várj egy percet", retryAfterMs = check.retryAfterMs)
                false
            }
        }
    }

    /** dir + optional "rel" sub-folders (folder uploads), confined to the allowed roots. */
    private suspend fun targetDir(call: ApplicationCall, body: ByteReadChannel? = null): File? {
        val dir = call.parameters["dir"].orEmpty()
        val rel = call.parameters["rel"].orEmpty().split('/').filter { it.isNotEmpty() }
        val resolved = withContext(Dispatchers.IO) { store.resolveDir(dir, rel) }
        if (resolved == null) {
            body?.discard()
            call.respondError(HttpStatusCode.Forbidden, "Ide nem lehet feltölteni")
        }
        return resolved
    }

    private fun list(dir: File): ListingDto {
        val children = dir.listFiles().orEmpty()
        val partials = children.filter { it.isFile && it.name.startsWith('.') && it.name.endsWith(UploadStore.PART_SUFFIX) }
        val visible = children.filterNot { it.name.startsWith('.') || it.name.startsWith('$') }
        return ListingDto(
            path = dir.path,
            dirs = visible.filter { it.isDirectory }.map { it.name }.sortedBy { it.lowercase() },
            files = visible.filter { it.isFile }.map { EntryDto(it.name, it.length()) }.sortedBy { it.name.lowercase() },
            partials = partials.map { PartialDto(it.name.removePrefix(".").removeSuffix(UploadStore.PART_SUFFIX), it.length()) },
            freeBytes = dir.usableSpace,
        )
    }

    private suspend inline fun <reified T> ApplicationCall.respondJson(value: T) {
        respondText(json.encodeToString(value), ContentType.Application.Json)
    }

    private suspend fun ApplicationCall.respondError(
        status: HttpStatusCode,
        message: String,
        received: Long? = null,
        retryAfterMs: Long? = null,
    ) {
        respondText(json.encodeToString(ErrorDto(message, received, retryAfterMs)), ContentType.Application.Json, status)
    }
}
